package com.company.erp.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String accountName,
        @NotBlank @Size(max = 60) String accountNumber,
        @Size(max = 255) String notes
) {
}
