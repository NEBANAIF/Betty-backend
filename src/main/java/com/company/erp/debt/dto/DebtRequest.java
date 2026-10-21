package com.company.erp.debt.dto;

import com.company.erp.debt.DebtType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Create or edit a debt. interestAmount is extra to repay on top of the principal (blank = 0). */
public record DebtRequest(
        @NotBlank @Size(max = 200) String lenderName,
        @NotNull DebtType debtType,
        @Size(max = 500) String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal principalAmount,
        @DecimalMin(value = "0.0") BigDecimal interestAmount,
        @NotNull LocalDate startDate,
        LocalDate dueDate,
        @Size(max = 1000) String notes
) {
}
