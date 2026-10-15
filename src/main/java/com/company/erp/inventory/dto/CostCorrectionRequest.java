package com.company.erp.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body for PATCH /api/inventory/products/{productId}/cost - corrects the
 * cost price recorded on a product's oldest in-stock batch (see
 * BatchCostCorrectionService). Gated by STOCK_ADJUST, same as any other
 * change to what stock is worth.
 */
public record CostCorrectionRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal costPrice
) {
}
