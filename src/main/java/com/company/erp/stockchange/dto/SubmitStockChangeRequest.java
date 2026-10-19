package com.company.erp.stockchange.dto;

import com.company.erp.stockchange.StockChangeType;
import com.company.erp.stockhistory.StockMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Enter a manual stock change; it is held until approved.
 *
 * ADD_BATCH (needs BATCH_CREATE): costPrice required; receivedDate optional (today, never in the future);
 *   expiryDate optional (for perishable/medical goods that track a shelf life); note optional.
 * REMOVE_STOCK (needs STOCK_ADJUST): movementType ADJUSTMENT / DAMAGED / LOST and a note (the reason) required;
 *   batchId optional - leave it out to take the oldest stock first.
 * EDIT_BATCH (needs BATCH_CREATE or STOCK_ADJUST): batchId required; batchNumber, costPrice and expiryDate are the
 *   values the batch should have once approved (a blank batchNumber or missing costPrice leaves it as is, an empty
 *   expiryDate clears it). productId / branchId / quantity must match the batch; quantity is never changed.
 * Fields that don't apply to the chosen type are ignored.
 */
public record SubmitStockChangeRequest(
        @NotNull StockChangeType changeType,
        @NotNull UUID productId,
        @NotNull UUID branchId,
        @NotNull @Positive Integer quantity,
        @PositiveOrZero BigDecimal costPrice,
        @PastOrPresent LocalDate receivedDate,
        LocalDate expiryDate,
        StockMovementType movementType,
        UUID batchId,
        @Size(max = 100) String batchNumber,
        @Size(max = 400) String note
) {
}
