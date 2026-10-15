package com.company.erp.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

/** receivedDate defaults to today if omitted. */
public record ReceivePurchaseRequest(
        LocalDate receivedDate,
        @NotEmpty @Valid List<ReceivePurchaseItemRequest> items
) {
}
