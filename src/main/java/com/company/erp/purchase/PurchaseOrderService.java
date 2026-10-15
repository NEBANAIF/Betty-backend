package com.company.erp.purchase;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.product.Product;
import com.company.erp.product.ProductRepository;
import com.company.erp.purchase.dto.CreatePurchaseOrderItemRequest;
import com.company.erp.purchase.dto.CreatePurchaseOrderRequest;
import com.company.erp.purchase.dto.PurchaseOrderResponse;
import com.company.erp.purchase.dto.ReceivePurchaseItemRequest;
import com.company.erp.purchase.dto.ReceivePurchaseRequest;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.supplier.Supplier;
import com.company.erp.supplier.SupplierRepository;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import com.company.erp.inventory.StockMutationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Purchasing workflow per spec section 17:
 * Supplier -> PurchaseOrder -> Receive Purchase -> automatic ProductBatch
 * creation -> increased branch stock. Receiving NEVER requires a separate
 * manual batch-creation step - receive(...) below is the only path into
 * StockMutationService.receiveStock for purchase-driven stock.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        branchAccessService.assertCanWriteToBranch(request.branchId());

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> ResourceNotFoundException.of("Supplier", request.supplierId()));
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", request.branchId()));
        User creator = currentUserEntity();

        PurchaseOrder po = new PurchaseOrder();
        po.setOrderNumber(generateOrderNumber());
        po.setSupplier(supplier);
        po.setBranch(branch);
        po.setOrderDate(request.orderDate());
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setCreatedBy(creator);

        BigDecimal total = BigDecimal.ZERO;
        for (CreatePurchaseOrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", itemRequest.productId()));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitCost(itemRequest.unitCost());
            item.setReceivedQuantity(0);
            po.addItem(item);

            total = total.add(item.getLineTotal());
        }
        po.setTotal(total);

        return PurchaseOrderResponse.from(purchaseOrderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderResponse submit(UUID id) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());
        requireStatus(po, "submit for approval", PurchaseOrderStatus.DRAFT);
        po.setStatus(PurchaseOrderStatus.PENDING);
        return PurchaseOrderResponse.from(po);
    }

    @Transactional
    public PurchaseOrderResponse approve(UUID id) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());
        requireStatus(po, "approve", PurchaseOrderStatus.PENDING);
        po.setStatus(PurchaseOrderStatus.APPROVED);
        po.setApprovedBy(currentUserEntity());
        return PurchaseOrderResponse.from(po);
    }

    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED || po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessRuleViolationException(
                    "Cannot cancel a purchase order that is already " + po.getStatus());
        }
        if (po.isPartiallyReceived()) {
            throw new BusinessRuleViolationException(
                    "Cannot cancel a purchase order that has already received stock - it must run to completion");
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return PurchaseOrderResponse.from(po);
    }

    /**
     * Receives some or all of the outstanding quantity on one or more
     * lines. Every unit received creates its own ProductBatch (via
     * StockMutationService.receiveStock) at that line's unit cost, and the
     * branch's Inventory + StockHistory are updated in the same
     * transaction. The PO's status is derived afterward from the items'
     * cumulative receivedQuantity - never set directly by the caller.
     */
    @Transactional
    public PurchaseOrderResponse receive(UUID id, ReceivePurchaseRequest request) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.assertCanWriteToBranch(po.getBranch().getId());

        if (po.getStatus() != PurchaseOrderStatus.APPROVED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessRuleViolationException(
                    "Purchase order must be APPROVED before it can be received (current status: "
                            + po.getStatus() + ")");
        }

        LocalDate receivedDate = request.receivedDate() != null ? request.receivedDate() : LocalDate.now();
        User receivedBy = currentUserEntity();

        Map<UUID, PurchaseOrderItem> itemsById = new HashMap<>();
        po.getItems().forEach(i -> itemsById.put(i.getId(), i));

        for (ReceivePurchaseItemRequest line : request.items()) {
            PurchaseOrderItem item = itemsById.get(line.purchaseOrderItemId());
            if (item == null) {
                throw new BusinessRuleViolationException(
                        "Purchase order item " + line.purchaseOrderItemId() + " does not belong to this order");
            }
            if (line.quantityReceived() > item.getRemainingToReceive()) {
                throw new BusinessRuleViolationException(
                        "Cannot receive " + line.quantityReceived() + " units for product " +
                                item.getProduct().getName() + " - only " + item.getRemainingToReceive() +
                                " remain outstanding on this order");
            }

            stockMutationService.receiveStock(
                    item.getProduct(), po.getBranch(), line.quantityReceived(), item.getUnitCost(),
                    receivedDate, StockMovementType.PURCHASE,
                    "Received against purchase order " + po.getOrderNumber(),
                    receivedBy, StockReferenceType.PURCHASE_ORDER, po.getId(), item.getId());

            item.setReceivedQuantity(item.getReceivedQuantity() + line.quantityReceived());
        }

        po.setStatus(po.isFullyReceived() ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);

        return PurchaseOrderResponse.from(po);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(UUID id) {
        PurchaseOrder po = findOrThrow(id);
        branchAccessService.resolveReadableBranchId(po.getBranch().getId());
        return PurchaseOrderResponse.from(po);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> search(BranchAccessService.BranchScope scope, UUID specificBranchId,
                                                        UUID supplierId, PurchaseOrderStatus status, String search,
                                                        UUID productId, java.time.LocalDate from, java.time.LocalDate to,
                                                        Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var page = purchaseOrderRepository.search(branchIds, supplierId, status, search, productId, from, to, pageable)
                .map(PurchaseOrderResponse::from);
        return PageResponse.of(page);
    }

    private void requireStatus(PurchaseOrder po, String action, PurchaseOrderStatus required) {
        if (po.getStatus() != required) {
            throw new BusinessRuleViolationException(
                    "Cannot " + action + " a purchase order in status " + po.getStatus() +
                            " (expected " + required + ")");
        }
    }

    private User currentUserEntity() {
        return userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();
    }

    private String generateOrderNumber() {
        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String candidate;
        do {
            int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
            candidate = "PO-" + stamp + "-" + suffix;
        } while (purchaseOrderRepository.existsByOrderNumber(candidate));
        return candidate;
    }

    private PurchaseOrder findOrThrow(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PurchaseOrder", id));
    }
}
