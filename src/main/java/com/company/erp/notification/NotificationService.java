package com.company.erp.notification;

import com.company.erp.branch.Branch;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.notification.dto.NotificationCountsResponse;
import com.company.erp.notification.dto.NotificationResponse;
import com.company.erp.product.Product;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The activity feed.
 *
 * Writing: sales and stock changes call record / recordStockChange from inside their own
 * transaction, so the notification is saved with the activity and disappears with it if the
 * activity is rolled back. Nothing here changes or blocks the activity itself.
 *
 * Reading: a user sees the notifications of the branches they may see (everything for the Super
 * Admin, their own branch otherwise). "Read" is per user and hides the notification from that
 * user's Unread list only. "Review" (hold / approve) is shared: it belongs to the activity.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** referenceType of the notification that announces a held manual stock change. */
    public static final String STOCK_CHANGE_REQUEST = "STOCK_CHANGE_REQUEST";

    private static final int MESSAGE_MAX = 500;

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;

    // ------------------------------------------------------------------ writing

    @Transactional
    public void record(NotificationType type, String title, String message, Branch branch, User actor,
                       String referenceType, UUID referenceId) {
        Notification n = new Notification();
        n.setType(type);
        n.setTitle(abbreviate(title, 150));
        n.setMessage(abbreviate(message, MESSAGE_MAX));
        n.setBranch(branch);
        n.setActor(actor);
        n.setReferenceType(referenceType);
        n.setReferenceId(referenceId);
        if (type == NotificationType.SALE_COMPLETED) {
            // A registered sale is already final - it needs nobody's approval, so it is born
            // approved and never shows up under "To review". It still appears in the Unread feed.
            n.setReviewStatus(ReviewStatus.APPROVED);
            n.setReviewedAt(Instant.now());
        } else {
            n.setReviewStatus(ReviewStatus.PENDING);
        }
        notificationRepository.save(n);
    }

    /**
     * A change to a branch's stock: quantity is signed (+ stock in, - stock out). Skipped here: sales
     * and sale voids (reported as one sale notification each) and manual changes (reported when they
     * are entered, see StockChangeService).
     */
    @Transactional
    public void recordStockChange(Product product, Branch branch, int quantity, StockMovementType movementType,
                                  String reason, User actor) {
        if (movementType == StockMovementType.SALE || movementType == StockMovementType.SALE_VOID) {
            return;
        }
        // Manual changes (added batch, adjustment, damaged, lost) are announced when they are
        // entered, as a stock change waiting for approval - not a second time when they are applied.
        if (movementType == StockMovementType.BATCH_ADDED || movementType == StockMovementType.ADJUSTMENT
                || movementType == StockMovementType.DAMAGED || movementType == StockMovementType.LOST) {
            return;
        }
        String unit = product.getUnit() == null || product.getUnit().isBlank() ? "" : " " + product.getUnit();
        String message = product.getName() + ": " + (quantity > 0 ? "+" : "") + quantity + unit
                + " at " + branch.getName()
                + (reason == null || reason.isBlank() ? "" : " — " + reason.trim());
        record(NotificationType.STOCK_CHANGE, stockTitle(movementType), message, branch, actor, null, null);
    }

    private static String stockTitle(StockMovementType type) {
        return switch (type) {
            case PURCHASE -> "Purchase received";
            case TRANSFER_OUT -> "Transfer sent out";
            case TRANSFER_IN -> "Transfer received";
            case CUSTOMER_RETURN -> "Customer return restocked";
            case SUPPLIER_RETURN -> "Returned to supplier";
            default -> "Stock changed";
        };
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(NotificationTab tab, Pageable pageable) {
        List<UUID> branchIds = visibleBranchIds();
        UUID userId = currentUserId();
        if (branchIds.isEmpty()) {
            return PageResponse.of(Page.empty(pageable));
        }
        Page<Notification> page = tab == NotificationTab.TO_REVIEW
                ? notificationRepository.findToReview(branchIds, pageable)
                : notificationRepository.findUnread(branchIds, userId, pageable);

        Set<UUID> readIds = new HashSet<>();
        if (!page.isEmpty()) {
            readIds.addAll(notificationReadRepository.findReadIds(userId,
                    page.getContent().stream().map(Notification::getId).toList()));
        }
        return PageResponse.of(page.map(n -> NotificationResponse.from(n, readIds.contains(n.getId()))));
    }

    @Transactional(readOnly = true)
    public NotificationCountsResponse counts() {
        List<UUID> branchIds = visibleBranchIds();
        if (branchIds.isEmpty()) {
            return new NotificationCountsResponse(0, 0);
        }
        return new NotificationCountsResponse(
                notificationRepository.countUnread(branchIds, currentUserId()),
                notificationRepository.countToReview(branchIds));
    }

    // ------------------------------------------------------------------ read state

    @Transactional
    public void markRead(UUID id) {
        Notification n = findVisible(id);
        ensureRead(n.getId(), currentUserId());
    }

    /** Marks everything this user can see as read. Returns how many were newly marked. */
    @Transactional
    public int markAllRead() {
        List<UUID> branchIds = visibleBranchIds();
        if (branchIds.isEmpty()) {
            return 0;
        }
        UUID userId = currentUserId();
        List<NotificationRead> reads = notificationRepository.findUnreadIds(branchIds, userId).stream()
                .map(nid -> new NotificationRead(nid, userId))
                .toList();
        notificationReadRepository.saveAll(reads);
        return reads.size();
    }

    // ------------------------------------------------------------------ review

    /** PENDING -> ON_HOLD: someone is checking this. */
    @Transactional
    public NotificationResponse hold(UUID id) {
        Notification n = findVisible(id);
        rejectIfStockChangeRequest(n);
        if (n.getReviewStatus() == ReviewStatus.ON_HOLD) {
            throw new BusinessRuleViolationException("This is already on hold");
        }
        if (n.getReviewStatus() == ReviewStatus.APPROVED) {
            throw new BusinessRuleViolationException("This has already been approved");
        }
        return review(n, ReviewStatus.ON_HOLD);
    }

    /** PENDING or ON_HOLD -> APPROVED: checked and correct. */
    @Transactional
    public NotificationResponse approve(UUID id) {
        Notification n = findVisible(id);
        rejectIfStockChangeRequest(n);
        if (n.getReviewStatus() == ReviewStatus.APPROVED) {
            throw new BusinessRuleViolationException("This has already been approved");
        }
        return review(n, ReviewStatus.APPROVED);
    }

    /**
     * A held stock change is approved or rejected on the stock change itself (that is what moves the
     * stock), never through the plain hold/approve of the feed.
     */
    private void rejectIfStockChangeRequest(Notification n) {
        if (STOCK_CHANGE_REQUEST.equals(n.getReferenceType())) {
            throw new BusinessRuleViolationException(
                    "This stock change is approved or rejected from the Batches page");
        }
    }

    /** The held stock change was approved or rejected: mirror that on its notification. */
    @Transactional
    public void resolveStockChangeRequest(UUID requestId, ReviewStatus status, User reviewer) {
        for (Notification n : notificationRepository.findByReferenceTypeAndReferenceId(STOCK_CHANGE_REQUEST, requestId)) {
            n.setReviewStatus(status);
            n.setReviewedBy(reviewer);
            n.setReviewedAt(Instant.now());
            ensureRead(n.getId(), reviewer.getId());
        }
    }

    private NotificationResponse review(Notification n, ReviewStatus status) {
        UUID userId = currentUserId();
        User reviewer = userRepository.findById(userId).orElseThrow();
        n.setReviewStatus(status);
        n.setReviewedBy(reviewer);
        n.setReviewedAt(Instant.now());
        // Whoever puts something on hold or approves it has, by definition, seen it.
        ensureRead(n.getId(), userId);
        return NotificationResponse.from(n, true);
    }

    // ------------------------------------------------------------------ helpers

    private List<UUID> visibleBranchIds() {
        return branchAccessService.resolveReadableBranchIds(BranchAccessService.BranchScope.ALL, null);
    }

    private UUID currentUserId() {
        return branchAccessService.currentUser().getUserId();
    }

    /** The notification, or "not found" when it belongs to a branch this user may not see. */
    private Notification findVisible(UUID id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        if (!visibleBranchIds().contains(n.getBranch().getId())) {
            throw ResourceNotFoundException.of("Notification", id);
        }
        return n;
    }

    private void ensureRead(UUID notificationId, UUID userId) {
        if (!notificationReadRepository.existsByNotificationIdAndUserId(notificationId, userId)) {
            notificationReadRepository.save(new NotificationRead(notificationId, userId));
        }
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
