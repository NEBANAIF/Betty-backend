package com.company.erp.returns.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * restock defaults to true when omitted (the common case: a customer
 * changes their mind about a good item). Set explicitly false for
 * damaged/defective returns that shouldn't go back into sellable stock.
 */
public record CreateCustomerReturnItemRequest(
        @NotNull UUID saleBatchAllocationId,
        @Positive int quantity,
        Boolean restock
) {
    public boolean restockOrDefault() {
        return restock == null || restock;
    }
}
