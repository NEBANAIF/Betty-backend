package com.company.erp.expense.dto;

import com.company.erp.expense.ExpenseCategory;
import com.company.erp.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseRequest(
        @NotNull UUID branchId,
        @NotNull ExpenseCategory category,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotBlank String description,
        @NotNull LocalDate expenseDate,
        @NotNull PaymentMethod paymentMethod,
        String reference
) {
}
