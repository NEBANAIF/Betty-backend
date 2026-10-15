package com.company.erp.returns.supplier.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateSupplierReturnRequest(
        @NotNull UUID purchaseOrderId,
        @NotEmpty @Valid List<CreateSupplierReturnItemRequest> items
) {
}
