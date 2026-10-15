package com.company.erp.bank.dto;

import jakarta.validation.constraints.NotNull;

public record BankStatusRequest(@NotNull Boolean active) {
}
