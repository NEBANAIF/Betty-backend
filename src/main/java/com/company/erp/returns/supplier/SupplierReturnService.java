package com.company.erp.returns.supplier;

import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ForbiddenException;
import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.inventory.StockMutationService;
import com.company.erp.purchase.PurchaseOrder;
import com.company.erp.purchase.PurchaseOrderItem;
import com.company.erp.purchase.PurchaseOrderRepository;
import com.company.erp.purchase.PurchaseOrderStatus;
import com.company.erp.returns.ReturnStatus;
import com.company.erp.returns.supplier.dto.CreateSupplierReturnRequest;
import com.company.erp.returns.supplier.dto.SupplierReturnResponse;
import com.company.erp.returns.supplier.dto.SupplierReturnSummaryResponse;
import com.company.erp.security.BranchAccessService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Supplier returns (spec section 22): select supplier (derived from the
 * purchase order, never independently chosen) -> select purchase ->
 * select items -> validate stock -> remove stock -> update batch -> stock
 * history -> create supplier return -> adjust supplier balance. One
 * @Transactional method, exactly like CustomerReturnService - see
 * ReturnStatus for why there's no staged workflow here.
 */
@Service
@RequiredArgsConstructor
public class SupplierReturnService {

    private final SupplierReturnRepository supplierReturnRepository;
    private final SupplierReturnItemRepository supplierReturnItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final StockMutationService stockMutationService;
    private final ProductBatchRepository productBatchRepository;

    @Transactional
    public SupplierReturnResponse create(CreateSupplierReturnRequest request) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(request.purchaseOrderId())
                .orElseThrow(() -> ResourceNotFoundException.of("Purchase order", request.purchaseOrderId()));

        // Only stock that was actually received can be sent back - a
        // DRAFT/PENDING/APPROVED order never created any batches yet.
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.RECEIVED
                && purchaseOrder.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessRuleViolationException(
                    "Purchase order " + purchaseOrder.getOrderNumber() + " is " + purchaseOrder.getStatus() +
                            " - only a RECEIVED or PARTIALLY_RECEIVED order has stock to return");
        }
        branchAccessService.assertCanWriteToBranch(purchaseOrder.getBranch().getId());

        User processor = currentUserEntity();

        SupplierReturn supplierReturn = new SupplierReturn();
        supplierReturn.setPurchaseOrder(purchaseOrder);
        supplierReturn.setSupplier(purchaseOrder.getSupplier());
        supplierReturn.setBranch(purchaseOrder.getBranch());
        supplierReturn.setStatus(ReturnStatus.COMPLETED);
        supplierReturn.setProcessedBy(processor);

        for (var itemRequest : request.items()) {
            PurchaseOrderItem poItem = findItemOnOrder(purchaseOrder, itemRequest.purchaseOrderItemId());

            int alreadyReturned = supplierReturnItemRepository.sumReturnedForPurchaseOrderItem(poItem.getId());
            int stillReturnable = poItem.getReceivedQuantity() - alreadyReturned;
            if (itemRequest.quantity() > stillReturnable) {
                throw new BusinessRuleViolationException(
                        "Cannot return " + itemRequest.quantity() + " units of " + poItem.getProduct().getName() +
                                " - only " + stillReturnable + " of the " + poItem.getReceivedQuantity() +
                                " received units remain returnable");
            }

            SupplierReturnItem item = new SupplierReturnItem();
            item.setPurchaseOrderItem(poItem);
            item.setQuantity(itemRequest.quantity());
            supplierReturn.addItem(item);

            // Scoped to batches created by THIS purchase-order line only -
            // never generic FIFO across the product+branch, which could
            // draw from (and misattribute cost to) an unrelated purchase
            // or a different supplier's shipment of the same product. See
            // ProductBatch.sourcePurchaseOrderItemId.
            var consumptions = stockMutationService.issueFromPurchaseOrderItem(
                    poItem.getProduct(), purchaseOrder.getBranch(), poItem.getId(), itemRequest.quantity(),
                    StockMovementType.SUPPLIER_RETURN,
                    "Supplier return against " + purchaseOrder.getOrderNumber(),
                    processor, StockReferenceType.SUPPLIER_RETURN, purchaseOrder.getId());

            for (var consumption : consumptions) {
                SupplierReturnAllocation allocation = new SupplierReturnAllocation();
                allocation.setBatch(productBatchRepository.findById(consumption.batchId()).orElseThrow());
                allocation.setQuantityAllocated(consumption.quantityConsumed());
                allocation.setUnitCost(consumption.unitCost());
                item.addAllocation(allocation);
            }
        }

        return SupplierReturnResponse.from(supplierReturnRepository.save(supplierReturn));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierReturnSummaryResponse> search(BranchAccessService.BranchScope scope,
                                                                UUID specificBranchId, UUID supplierId,
                                                                UUID purchaseOrderId, java.time.Instant from, java.time.Instant to, String search,
                                                                Pageable pageable) {
        var branchIds = branchAccessService.resolveReadableBranchIds(scope, specificBranchId);
        var page = supplierReturnRepository.search(branchIds, supplierId, purchaseOrderId, from, to, search, pageable)
                .map(SupplierReturnSummaryResponse::from);
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public SupplierReturnResponse get(UUID id) {
        SupplierReturn supplierReturn = supplierReturnRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Supplier return", id));
        branchAccessService.resolveReadableBranchId(supplierReturn.getBranch().getId());
        return SupplierReturnResponse.from(supplierReturn);
    }

    private PurchaseOrderItem findItemOnOrder(PurchaseOrder purchaseOrder, UUID purchaseOrderItemId) {
        return purchaseOrder.getItems().stream()
                .filter(i -> i.getId().equals(purchaseOrderItemId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException(
                        "Item " + purchaseOrderItemId + " does not belong to purchase order " +
                                purchaseOrder.getOrderNumber()));
    }

    private User currentUserEntity() {
        return userRepository.findById(branchAccessService.currentUser().getUserId()).orElseThrow();
    }
}
