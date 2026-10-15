package com.company.erp.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One product's stock position, derived entirely from its batches that
 * still have stock.
 *
 * oldestCost = cost of the oldest batch still in stock, i.e. what FIFO will
 * cost the next sale at (for a single branch). latestCost = cost of the most
 * recently received batch (the newest purchase price). stockValue = the sum
 * of remainingQuantity x batch cost, so units bought at different prices are
 * each valued at what they actually cost.
 */
public record ProductCostRow(
        UUID productId,
        int quantityOnHand,
        BigDecimal oldestCost,
        BigDecimal latestCost,
        BigDecimal stockValue
) {
}
