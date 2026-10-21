package com.company.erp.expense.dto;

import com.company.erp.expense.Expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID branchId,
        String branchName,
        String category,
        BigDecimal amount,
        String description,
        LocalDate expenseDate,
        String paymentMethod,
        String reference,
        /** Sale number when this expense was entered for a specific sale at checkout, else null. */
        String saleNumber,
        String createdByName,
        Instant createdAt
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(), e.getBranch().getId(), e.getBranch().getName(), e.getCategory().name(),
                e.getAmount(), e.getDescription(), e.getExpenseDate(), e.getPaymentMethod().name(),
                e.getReference(), e.getSale() != null ? e.getSale().getSaleNumber() : null,
                e.getCreatedBy().getName(), e.getCreatedAt());
    }
}
