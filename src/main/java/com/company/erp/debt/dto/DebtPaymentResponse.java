package com.company.erp.debt.dto;

import com.company.erp.debt.DebtPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DebtPaymentResponse(
        UUID id,
        BigDecimal amount,
        LocalDate paymentDate,
        String method,
        String reference,
        String note,
        String createdByName,
        Instant createdAt
) {
    public static DebtPaymentResponse from(DebtPayment p) {
        return new DebtPaymentResponse(p.getId(), p.getAmount(), p.getPaymentDate(), p.getMethod().name(),
                p.getReference(), p.getNote(), p.getCreatedBy().getName(), p.getCreatedAt());
    }
}
