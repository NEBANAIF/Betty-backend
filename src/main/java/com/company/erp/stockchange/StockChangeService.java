package com.company.erp.stockchange;

import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.inventory.Inventory;
import com.company.erp.inventory.InventoryRepository;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.notification.NotificationService;
import com.company.erp.notification.NotificationType;
import com.company.erp.notification.ReviewStatus;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.security.BranchAccessService;
import com.company.erp.security.ContentAccess;
import com.company.erp.security.UserPrincipal;
import com.company.erp.stockchange.dto.StockChangeResponse;
import com.company.erp.stockchange.dto.SubmitStockChangeRequest;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.Permission;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manual stock changes that wait to be checked before they count.
 *
 * submit: whoever adds a batch or removes stock by hand enters it; NOTHING moves yet. It is saved
 *   exactly as typed and a notification is raised for the reviewers.
 * approve: the reviewer has seen what was entered; only now does the stock move, through
 *   StockMutationService like every other stock change (so the batch, the branch total and Stock
 *   History are written together). If it can no longer be applied - say the stock it would remove
 *   has since been sold - the approval fails, the request stays PENDING, and nothing changes.
 * reject: discards it; the stock never moved.
 *
 * Approving one's own entry is allowed on purpose: the point is to look again at what was typed
 * (5 instead of 50) before it counts.
 */
@Service
@RequiredArgsConstructor
public class StockChangeService {

    private static final Set<StockMovementType> REMOVAL_REASONS =
            Set.of(StockMovementType.ADJUSTMENT, StockMovementType.DAMAGED, StockMovementType.LOST);

    private final StockChangeRequestRepository requestRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final ProductBatchRepository productBatchRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;
    private final NotificationService notificationService;
    private final ContentAccess contentAccess;

    // ------------------------------------------------------------------ submit

    @Transactional
    public StockChangeResponse submit(SubmitStockChangeRequest request) {
        // request.branchId() is only a target - this is where it is checked against what the
        // signed-in user may actually write to.
        branchAccessService.assertCanWriteToBranch(request.branchId());

        UserPrincipal caller = branchAccessService.currentUser();
        boolean adding = request.changeType() == StockChangeType.ADD_BATCH;
        if (!caller.hasPermission(adding ? Permission.BATCH_CREATE : Permission.STOCK_ADJUST)) {
            throw new ForbiddenException(adding
                    ? "Your role is not allowed to add batches"
                    : "Your role is not allowed to remove stock");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", request.productId()));
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", request.branchId()));
        User requester = userRepository.findById(caller.getUserId()).orElseThrow();

        StockChangeRequest change = new StockChangeRequest();
        change.setChangeType(request.changeType());
        change.setProduct(product);
        change.setBranch(branch);
        change.setQuantity(request.quantity());
        change.setRequestedBy(requester);
        change.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());

        if (adding) {
            if (request.costPrice() == null) {
                throw new BusinessRuleViolationException("Enter what one unit cost");
            }
            change.setCostPrice(request.costPrice());
            change.setReceivedDate(request.receivedDate() != null ? request.receivedDate() : LocalDate.now());
            change.setExpiryDate(request.expiryDate());
        } else {
            if (request.movementType() == null || !REMOVAL_REASONS.contains(request.movementType())) {
                throw new BusinessRuleViolationException("Choose why the stock is being removed");
            }
            if (change.getNote() == null) {
                throw new BusinessRuleViolationException("Write a short reason for removing this stock");
            }
            change.setMovementType(request.movementType());
            change.setBatch(checkRemovable(product, branch, request.batchId(), request.quantity()));
        }

        change = requestRepository.save(change);

        notificationService.record(NotificationType.STOCK_CHANGE,
                "Stock change waiting for approval", describe(change), branch, requester,
                NotificationService.STOCK_CHANGE_REQUEST, change.getId());

        return toResponse(change, caller);
    }

    // ------------------------------------------------------------------ approve / reject

    @Transactional
    public StockChangeResponse approve(UUID id) {
        StockChangeRequest change = findPending(id);
        UserPrincipal caller = branchAccessService.currentUser();
        User approver = userRepository.findById(caller.getUserId()).orElseThrow();

        Product product = change.getProduct();
        Branch branch = change.getBranch();
        // The stock history records who ENTERED it; the reason records who approved it.
        User enteredBy = change.getRequestedBy();

        if (change.getChangeType() == StockChangeType.ADD_BATCH) {
            stockMutationService.receiveStock(product, branch, change.getQuantity(), change.getCostPrice(),
                    change.getReceivedDate(), StockMovementType.BATCH_ADDED,
                    reason(change.getNote(), "Batch added manually", approver), enteredBy,
                    StockReferenceType.MANUAL_ADJUSTMENT, change.getId(), null, change.getExpiryDate());
        } else if (change.getBatch() != null) {
            stockMutationService.issueFromBatch(product, branch, change.getBatch().getId(), change.getQuantity(),
                    change.getMovementType(), reason(change.getNote(), "Stock removed manually", approver),
                    enteredBy, StockReferenceType.MANUAL_ADJUSTMENT, change.getId());
        } else {
            stockMutationService.issueStock(product, branch, change.getQuantity(), change.getMovementType(),
                    reason(change.getNote(), "Stock removed manually", approver), enteredBy,
                    StockReferenceType.MANUAL_ADJUSTMENT, change.getId());
        }

        change.setStatus(StockChangeStatus.APPROVED);
        change.setReviewedBy(approver);
        change.setReviewedAt(Instant.now());
        notificationService.resolveStockChangeRequest(change.getId(), ReviewStatus.APPROVED, approver);
        return toResponse(change, caller);
    }

    /** Discard a held change. Allowed for someone who can approve stock changes, or for whoever entered it. */
    @Transactional
    public StockChangeResponse reject(UUID id, String note) {
        StockChangeRequest change = findPending(id);
        UserPrincipal caller = branchAccessService.currentUser();
        boolean canApprove = caller.hasPermission(Permission.STOCK_APPROVE);
        boolean entered = change.getRequestedBy().getId().equals(caller.getUserId());
        if (!canApprove && !entered) {
            throw new ForbiddenException(
                    "Only the person who entered this, or someone who can approve stock changes, can discard it");
        }
        User reviewer = userRepository.findById(caller.getUserId()).orElseThrow();

        change.setStatus(StockChangeStatus.REJECTED);
        change.setReviewedBy(reviewer);
        change.setReviewedAt(Instant.now());
        change.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        notificationService.resolveStockChangeRequest(change.getId(), ReviewStatus.REJECTED, reviewer);
        return toResponse(change, caller);
    }

    // ------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public PageResponse<StockChangeResponse> list(StockChangeStatus status, Pageable pageable) {
        List<UUID> branchIds = branchAccessService.resolveReadableBranchIds(BranchAccessService.BranchScope.ALL, null);
        if (branchIds.isEmpty()) {
            return PageResponse.of(Page.empty(pageable));
        }
        UserPrincipal caller = branchAccessService.currentUser();
        return PageResponse.of(requestRepository
                .findByBranchIdInAndStatusOrderByCreatedAtDesc(branchIds, status, pageable)
                .map(r -> toResponse(r, caller)));
    }

    // ------------------------------------------------------------------ helpers

    private StockChangeRequest findPending(UUID id) {
        StockChangeRequest change = requestRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Stock change", id));
        branchAccessService.assertCanWriteToBranch(change.getBranch().getId());
        if (change.getStatus() != StockChangeStatus.PENDING) {
            throw new BusinessRuleViolationException("This stock change was already "
                    + change.getStatus().name().toLowerCase());
        }
        return change;
    }

    /**
     * Early feedback when a removal is entered: the batch (if one was chosen) belongs to that
     * product and branch and holds enough, or the branch as a whole has enough. Checked again
     * for real when it is approved, because stock can be sold in between.
     */
    private ProductBatch checkRemovable(Product product, Branch branch, UUID batchId, int quantity) {
        if (batchId != null) {
            ProductBatch batch = productBatchRepository.findById(batchId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Batch", batchId));
            if (!batch.getProduct().getId().equals(product.getId()) || !batch.getBranch().getId().equals(branch.getId())) {
                throw new BusinessRuleViolationException("That batch isn't " + product.getName() + " at " + branch.getName());
            }
            if (batch.getRemainingQuantity() < quantity) {
                throw new BusinessRuleViolationException("Batch " + batch.getBatchNumber() + " only has "
                        + batch.getRemainingQuantity() + " left");
            }
            return batch;
        }
        int available = inventoryRepository.findByProductIdAndBranchId(product.getId(), branch.getId())
                .map(Inventory::getAvailableQuantity).orElse(0);
        if (available < quantity) {
            throw new BusinessRuleViolationException("Only " + available + " of " + product.getName()
                    + " available at " + branch.getName());
        }
        return null;
    }

    private String describe(StockChangeRequest c) {
        String unit = c.getProduct().getUnit() == null || c.getProduct().getUnit().isBlank() ? "" : " " + c.getProduct().getUnit();
        String what = c.getChangeType() == StockChangeType.ADD_BATCH
                ? "Add " + c.getQuantity() + unit + " of " + c.getProduct().getName()
                : "Remove " + c.getQuantity() + unit + " of " + c.getProduct().getName()
                        + " (" + removalLabel(c.getMovementType()) + ")";
        return what + " at " + c.getBranch().getName() + (c.getNote() == null ? "" : " — " + c.getNote());
    }

    private static String removalLabel(StockMovementType type) {
        return switch (type) {
            case DAMAGED -> "damaged";
            case LOST -> "lost";
            default -> "correction";
        };
    }

    private static String reason(String note, String fallback, User approver) {
        String base = note == null || note.isBlank() ? fallback : note.trim();
        String text = base + " (approved by " + approver.getName() + ")";
        return text.length() <= 500 ? text : text.substring(0, 499) + "…";
    }

    private StockChangeResponse toResponse(StockChangeRequest change, UserPrincipal caller) {
        boolean mine = change.getRequestedBy().getId().equals(caller.getUserId());
        return StockChangeResponse.from(change, contentAccess.can(Permission.VIEW_COSTS), mine);
    }
}
