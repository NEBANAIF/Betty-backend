package com.company.erp.inventory;

import com.company.erp.batch.BatchConsumption;
import com.company.erp.batch.BatchService;
import com.company.erp.batch.FifoConsumptionService;
import com.company.erp.batch.ProductBatch;
import com.company.erp.batch.ProductBatchRepository;
import com.company.erp.branch.Branch;
import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.notification.NotificationService;
import com.company.erp.product.Product;
import com.company.erp.stockhistory.StockHistoryService;
import com.company.erp.stockhistory.StockMovementType;
import com.company.erp.stockhistory.StockReferenceType;
import com.company.erp.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * THE single entry point for every operation that changes stock. No other
 * service in the codebase should touch ProductBatch.remainingQuantity or
 * Inventory.quantity directly - Purchasing, POS/Sales, Transfers, Returns,
 * and manual adjustments all call one of the three methods below, and each
 * one is @Transactional so a failure partway through (e.g. the stock
 * history write fails) rolls back the batch and inventory changes too,
 * per spec section 33.
 *
 * Each operation also raises one Notification (except sales and sale voids, which
 * are reported as a sale) so the change shows up in the activity feed.
 *
 * This is also why StockHistory is guaranteed to be a complete ledger:
 * there is no code path that mutates stock without also calling
 * stockHistoryService.record(...) in the same method.
 */
@Service
@RequiredArgsConstructor
public class StockMutationService {

    private final InventoryService inventoryService;
    private final BatchService batchService;
    private final FifoConsumptionService fifoConsumptionService;
    private final ProductBatchRepository productBatchRepository;
    private final StockHistoryService stockHistoryService;
    private final NotificationService notificationService;

    /**
     * Stock coming IN: purchase receiving, transfer receiving, or a manual
     * "found extra stock" adjustment. Always creates a new batch - the
     * caller decides the cost price (the purchase order's unit cost, the
     * transfer's preserved source cost, or an admin-entered cost for an
     * adjustment).
     */
    @Transactional
    public ProductBatch receiveStock(Product product, Branch branch, int quantity, BigDecimal costPrice,
                                      LocalDate receivedDate, StockMovementType movementType, String reason,
                                      User user, StockReferenceType referenceType, UUID referenceId) {
        return receiveStock(product, branch, quantity, costPrice, receivedDate, movementType, reason,
                user, referenceType, referenceId, null);
    }

    /**
     * Overload used by purchase receiving to additionally stamp the
     * created batch with the PurchaseOrderItem that caused it - see
     * ProductBatch.sourcePurchaseOrderItemId. Every other caller (transfer
     * receiving, manual adjustments) uses the shorter overload above,
     * which passes null.
     */
    @Transactional
    public ProductBatch receiveStock(Product product, Branch branch, int quantity, BigDecimal costPrice,
                                      LocalDate receivedDate, StockMovementType movementType, String reason,
                                      User user, StockReferenceType referenceType, UUID referenceId,
                                      UUID sourcePurchaseOrderItemId) {
        return receiveStock(product, branch, quantity, costPrice, receivedDate, movementType, reason,
                user, referenceType, referenceId, sourcePurchaseOrderItemId, null);
    }

    /**
     * Overload used by manual "Add Batch" stock changes to additionally
     * record when this batch expires (optional - perishable/medical goods
     * such as cosmetics). Every other caller passes null via one of the
     * shorter overloads above.
     */
    @Transactional
    public ProductBatch receiveStock(Product product, Branch branch, int quantity, BigDecimal costPrice,
                                      LocalDate receivedDate, StockMovementType movementType, String reason,
                                      User user, StockReferenceType referenceType, UUID referenceId,
                                      UUID sourcePurchaseOrderItemId, LocalDate expiryDate) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Received quantity must be positive");
        }

        ProductBatch batch = batchService.createBatch(
                product, branch, quantity, costPrice, receivedDate, sourcePurchaseOrderItemId, expiryDate);

        Inventory inventory = inventoryService.getOrCreateForUpdate(product, branch);
        int previous = inventoryService.increase(inventory, quantity);

        stockHistoryService.record(product, branch, batch, movementType, quantity,
                previous, inventory.getQuantity(), reason, user, referenceType, referenceId);
        notificationService.recordStockChange(product, branch, quantity, movementType, reason, user);

        return batch;
    }

    /**
     * Stock going OUT: a sale, a transfer's source-side deduction, a
     * supplier return, or a manual "damaged/lost" adjustment. Consumes
     * FIFO across batches and writes one StockHistory row per batch touched
     * (so the ledger shows exactly which batch/cost each unit came from),
     * plus decrements the Inventory aggregate once for the total.
     *
     * Returns the batch consumption breakdown so the caller (POS checkout,
     * for example) can compute COGS from real batch costs.
     */
    @Transactional
    public List<BatchConsumption> issueStock(Product product, Branch branch, int quantity,
                                              StockMovementType movementType, String reason, User user,
                                              StockReferenceType referenceType, UUID referenceId) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Issued quantity must be positive");
        }
        List<BatchConsumption> consumptions = fifoConsumptionService.consume(product.getId(), branch.getId(), quantity);
        return applyIssuedConsumptions(product, branch, quantity, consumptions, movementType, reason, user,
                referenceType, referenceId);
    }

    /**
     * Same as issueStock, but the whole quantity comes from ONE chosen batch
     * instead of FIFO across the product's batches - used by the POS when the
     * cashier picks which batch is being sold. The sale is then costed at that
     * batch's cost, and the stock history/allocation rows point at it.
     */
    @Transactional
    public List<BatchConsumption> issueFromBatch(Product product, Branch branch, UUID batchId, int quantity,
                                                  StockMovementType movementType, String reason, User user,
                                                  StockReferenceType referenceType, UUID referenceId) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Issued quantity must be positive");
        }
        List<BatchConsumption> consumptions =
                fifoConsumptionService.consumeFromBatch(batchId, product.getId(), branch.getId(), quantity);
        return applyIssuedConsumptions(product, branch, quantity, consumptions, movementType, reason, user,
                referenceType, referenceId);
    }

    /**
     * Same as issueStock, but scoped to only the batch(es) created by one
     * specific purchase-order line - used exclusively by Supplier Returns,
     * which must remove stock from the exact purchase being returned
     * against, never FIFO across every batch of the product at the branch
     * (which could draw from, and misattribute cost to, an unrelated
     * purchase or a different supplier's shipment of the same product).
     */
    @Transactional
    public List<BatchConsumption> issueFromPurchaseOrderItem(Product product, Branch branch,
                                                              UUID purchaseOrderItemId, int quantity,
                                                              StockMovementType movementType, String reason,
                                                              User user, StockReferenceType referenceType,
                                                              UUID referenceId) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Issued quantity must be positive");
        }
        List<BatchConsumption> consumptions =
                fifoConsumptionService.consumeFromPurchaseOrderItem(purchaseOrderItemId, quantity);
        return applyIssuedConsumptions(product, branch, quantity, consumptions, movementType, reason, user,
                referenceType, referenceId);
    }

    /**
     * Shared tail of both issue* methods above, once the batch-level
     * consumption plan has already been decided (FIFO-wide or
     * PO-item-scoped): decrement the Inventory aggregate once for the
     * total, and write one StockHistory row per batch touched so the
     * ledger shows exactly which batch/cost each unit came from.
     */
    private List<BatchConsumption> applyIssuedConsumptions(Product product, Branch branch, int quantity,
                                                             List<BatchConsumption> consumptions,
                                                             StockMovementType movementType, String reason,
                                                             User user, StockReferenceType referenceType,
                                                             UUID referenceId) {
        Inventory inventory = inventoryService.getOrCreateForUpdate(product, branch);
        int previousTotal = inventoryService.decrease(inventory, quantity);
        int newTotal = inventory.getQuantity();

        int runningBefore = previousTotal;
        for (BatchConsumption consumption : consumptions) {
            ProductBatch batch = productBatchRepository.findById(consumption.batchId()).orElseThrow();
            int runningAfter = runningBefore - consumption.quantityConsumed();
            stockHistoryService.record(product, branch, batch, movementType,
                    -consumption.quantityConsumed(), runningBefore, runningAfter, reason,
                    user, referenceType, referenceId);
            runningBefore = runningAfter;
        }
        // Guard against the per-batch history math drifting from the
        // aggregate decrement (would indicate a bug in the loop above).
        if (runningBefore != newTotal) {
            throw new IllegalStateException(
                    "Stock history reconciliation mismatch for product " + product.getId());
        }
        notificationService.recordStockChange(product, branch, -quantity, movementType, reason, user);

        return consumptions;
    }

    /**
     * Restocks a specific quantity back into a specific batch (preserving
     * its original cost), for customer returns. Distinct from receiveStock
     * because a return does NOT create a new batch - it reverses a prior
     * consumption from a known batch.
     */
    @Transactional
    public void restockToBatch(Product product, Branch branch, UUID batchId, int quantity,
                                StockMovementType movementType, String reason, User user,
                                StockReferenceType referenceType, UUID referenceId) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Restocked quantity must be positive");
        }

        fifoConsumptionService.restoreToBatch(batchId, quantity);
        ProductBatch batch = productBatchRepository.findById(batchId).orElseThrow();

        Inventory inventory = inventoryService.getOrCreateForUpdate(product, branch);
        int previous = inventoryService.increase(inventory, quantity);

        stockHistoryService.record(product, branch, batch, movementType, quantity,
                previous, inventory.getQuantity(), reason, user, referenceType, referenceId);
        notificationService.recordStockChange(product, branch, quantity, movementType, reason, user);
    }
}
