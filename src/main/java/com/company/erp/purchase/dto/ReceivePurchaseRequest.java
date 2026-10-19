package com.company.erp.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * receivedDate defaults to today if omitted. {@code requestId} is a fresh UUID made by the screen for each
 * receive dialog: sending the same one twice is refused, so a double click can never add the stock twice.
 */
public record ReceivePurchaseRequest(
        LocalDate receivedDate,
        @NotEmpty @Valid List<ReceivePurchaseItemRequest> items,
        UUID requestId,
        @Size(max = 300) String note
) {
    public ReceivePurchaseRequest(LocalDate receivedDate, List<ReceivePurchaseItemRequest> items) {
        this(receivedDate, items, null, null);
    }
}
