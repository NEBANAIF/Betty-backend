package com.company.erp.transfer.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectTransferRequest(
        @NotBlank String reason
) {
}
