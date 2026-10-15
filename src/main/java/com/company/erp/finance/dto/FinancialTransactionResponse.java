package com.company.erp.finance.dto;

import com.company.erp.finance.FinancialTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialTransactionResponse(
        UUID id,
        UUID branchId,
        String branchName,
        String type,
        BigDecimal amount,
        LocalDate occurredOn,
        String referenceType,
        UUID referenceId
) {
    public static FinancialTransactionResponse from(FinancialTransaction t) {
        return new FinancialTransactionResponse(
                t.getId(), t.getBranch().getId(), t.getBranch().getName(), t.getType().name(),
                t.getAmount(), t.getOccurredOn(), t.getReferenceType().name(), t.getReferenceId());
    }
}
