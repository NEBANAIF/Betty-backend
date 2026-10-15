package com.company.erp.purchase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReceivePurchaseItemRequest(
        @NotNull UUID purchaseOrderItemId,
        @Positive int quantityReceived
) {
}
