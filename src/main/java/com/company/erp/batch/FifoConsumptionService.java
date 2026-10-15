package com.company.erp.batch;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.InsufficientStockException;
import com.company.erp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * THE single implementation of "consume N units of this product at this
 * branch, FIFO, and tell me exactly which batches (and therefore which
 * costs) they came from". Used by:
 * - POS checkout (Phase 6) to compute COGS at the moment of sale
 * - Stock Transfer IN_TRANSIT (Phase 8) to pull stock out of the source
 *   branch while preserving each unit's original cost
 *
 * Supplier Returns use the sibling method consumeFromPurchaseOrderItem
 * below instead - a return must draw only from the specific purchase
 * being returned against, never FIFO across every batch of the product.
 *
 * Every caller MUST run this inside the same @Transactional boundary as
 * the Inventory decrement and StockHistory write - this service only
 * touches ProductBatch rows, nothing else, on purpose (single
 * responsibility; the orchestration lives in StockMutationService).
 *
 * Concurrency: findConsumableForUpdate takes PESSIMISTIC_WRITE row locks
 * on every candidate batch before this method does any arithmetic, so two
 * concurrent consumptions against the same product+branch cannot both read
 * the same remainingQuantity and double-spend it - the second transaction
 * blocks until the first commits or rolls back.
 */
@Service
@RequiredArgsConstructor
public class FifoConsumptionService {

    private final ProductBatchRepository productBatchRepository;

    @Transactional
    public List<BatchConsumption> consume(UUID productId, UUID branchId, int quantityNeeded) {
        List<ProductBatch> batches = productBatchRepository.findConsumableForUpdate(productId, branchId);
        return consumeFrom(batches, quantityNeeded);
    }

    /**
     * Same FIFO consumption, but scoped to only the batch(es) created by
     * ONE SPECIFIC purchase-order line, rather than every batch of a
     * product at a branch. This is what Supplier Returns uses instead of
     * {@link #consume} - removing stock against a specific purchase must
     * never draw from (and misattribute cost/value to) a different
     * purchase or a different supplier's shipment of the same product.
     */
    @Transactional
    public List<BatchConsumption> consumeFromPurchaseOrderItem(UUID purchaseOrderItemId, int quantityNeeded) {
        List<ProductBatch> batches = productBatchRepository
                .findConsumableForUpdateByPurchaseOrderItem(purchaseOrderItemId);
        return consumeFrom(batches, quantityNeeded);
    }

    /**
     * Consume from ONE specific batch chosen by the caller (the POS "sell from
     * this batch" option), instead of FIFO across all of a product's batches.
     * The batch row is locked first (same PESSIMISTIC_WRITE guarantee as the
     * FIFO queries), it must belong to this product at this branch, and it
     * must hold the whole quantity - we never silently top up from another
     * batch, because that would cost the sale differently than the cashier chose.
     */
    @Transactional
    public List<BatchConsumption> consumeFromBatch(UUID batchId, UUID productId, UUID branchId, int quantityNeeded) {
        ProductBatch batch = productBatchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Batch", batchId));

        if (!batch.getProduct().getId().equals(productId) || !batch.getBranch().getId().equals(branchId)) {
            throw new BusinessRuleViolationException(
                    "Batch " + batch.getBatchNumber() + " is not a batch of this product at this branch");
        }
        if (quantityNeeded > batch.getRemainingQuantity()) {
            throw new InsufficientStockException("Batch " + batch.getBatchNumber() + " has only "
                    + batch.getRemainingQuantity() + " left but " + quantityNeeded + " were requested");
        }
        return consumeFrom(List.of(batch), quantityNeeded);
    }

    private List<BatchConsumption> consumeFrom(List<ProductBatch> batches, int quantityNeeded) {
        if (quantityNeeded <= 0) {
            throw new IllegalArgumentException("quantityNeeded must be positive");
        }

        int totalAvailable = batches.stream().mapToInt(ProductBatch::getRemainingQuantity).sum();
        if (totalAvailable < quantityNeeded) {
            throw new InsufficientStockException(
                    "Insufficient stock: requested " + quantityNeeded + " but only " + totalAvailable + " available");
        }

        List<BatchConsumption> consumptions = new ArrayList<>();
        int remainingToConsume = quantityNeeded;

        for (ProductBatch batch : batches) {
            if (remainingToConsume == 0) {
                break;
            }
            int takeFromThisBatch = Math.min(batch.getRemainingQuantity(), remainingToConsume);
            batch.setRemainingQuantity(batch.getRemainingQuantity() - takeFromThisBatch);
            remainingToConsume -= takeFromThisBatch;

            consumptions.add(new BatchConsumption(
                    batch.getId(), batch.getBatchNumber(), takeFromThisBatch, batch.getCostPrice()));
        }

        return consumptions;
    }

    /**
     * Reverses a consumption - used by Customer Returns to put stock back
     * into the exact batches (and therefore costs) it originally came from.
     * Never creates a new batch; if a batchId referenced by a return no
     * longer exists this is a data-integrity error, not something to
     * silently paper over with a fresh batch at today's cost.
     */
    @Transactional
    public void restoreToBatch(UUID batchId, int quantity) {
        ProductBatch batch = productBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot restore stock: batch " + batchId + " no longer exists"));
        batch.setRemainingQuantity(batch.getRemainingQuantity() + quantity);
    }
}
