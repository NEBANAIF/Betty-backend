package com.company.erp.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceBranchId,
        @NotNull UUID destinationBranchId,
        @NotEmpty @Valid List<CreateTransferItemRequest> items
) {
}
