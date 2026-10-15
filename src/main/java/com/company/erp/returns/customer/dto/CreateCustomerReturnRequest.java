package com.company.erp.returns.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateCustomerReturnRequest(
        @NotNull UUID saleId,
        @NotEmpty @Valid List<CreateCustomerReturnItemRequest> items
) {
}
