package com.company.erp.loan.dto;

import com.company.erp.payment.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordLoanPaymentRequest(
        @NotNull PaymentMethod method,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        String bankName,
        String accountReference,
        UUID bankId
) {
}
