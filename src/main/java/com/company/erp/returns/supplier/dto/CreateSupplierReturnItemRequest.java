package com.company.erp.returns.supplier.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateSupplierReturnItemRequest(
        @NotNull UUID purchaseOrderItemId,
        @Positive int quantity
) {
}
