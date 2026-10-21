package com.company.erp.debt.dto;

import com.company.erp.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtPaymentRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        LocalDate paymentDate,
        @NotNull PaymentMethod method,
        @Size(max = 255) String reference,
        @Size(max = 400) String note
) {
}
