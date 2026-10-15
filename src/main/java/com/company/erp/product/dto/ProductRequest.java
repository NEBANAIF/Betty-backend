package com.company.erp.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductRequest(
        @NotBlank String name,
        @NotBlank String sku,
        String description,
        @NotBlank String unit,
        @PositiveOrZero Integer reorderLevel
) {
}
