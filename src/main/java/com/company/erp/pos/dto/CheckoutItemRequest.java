package com.company.erp.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * unitPrice is the price the cashier charges for this line - a product has no
 * stored selling price, so it must always be supplied.
 * <p>
 * batchId is optional. Left out, stock is taken oldest-batch-first (FIFO) as
 * usual. Given, the whole quantity is taken from that one batch (which must be
 * a batch of this product at the sale's branch, with enough left), so the sale
 * is costed at exactly that batch's cost.
 */
public record CheckoutItemRequest(
        @NotNull UUID productId,
        @Positive int quantity,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal unitPrice,
        UUID batchId
) {
}
