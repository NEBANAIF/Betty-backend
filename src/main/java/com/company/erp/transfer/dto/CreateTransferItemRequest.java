package com.company.erp.transfer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateTransferItemRequest(
        @NotNull UUID productId,
        @Positive int quantity
) {
}
