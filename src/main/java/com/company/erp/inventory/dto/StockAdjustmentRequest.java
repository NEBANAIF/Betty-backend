package com.company.erp.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A manual correction to stock, gated by the STOCK_ADJUST permission.
 *
 * direction=INCREASE creates a new batch at costPrice (required in that
 * case - an admin correcting "we found 20 more units" needs to say what
 * they're worth). direction=DECREASE consumes FIFO, using
 * movementType ADJUSTMENT, DAMAGED, or LOST to record *why* stock left.
 */
public record StockAdjustmentRequest(
        @NotNull UUID productId,
        @NotNull UUID branchId,
        @NotNull AdjustmentDirection direction,
        @NotNull Integer quantity,
        BigDecimal costPrice,
        @NotNull com.company.erp.stockhistory.StockMovementType movementType,
        @NotBlank String reason
) {
    public enum AdjustmentDirection { INCREASE, DECREASE }
}
