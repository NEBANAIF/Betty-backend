package com.company.erp.payment.dto;

import com.company.erp.payment.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * bankAccountNumber is the registered bank's account number and is only filled in for roles with
 * the BANK_VIEW switch (see PaymentQueryService); accountReference is the transfer/receipt
 * reference the cashier typed, which is not sensitive. referenceNumber is the sale number the
 * payment belongs to (for a loan repayment, the sale the loan came from).
 */
public record PaymentResponse(
        UUID id,
        String method,
        BigDecimal amount,
        UUID branchId,
        String branchName,
        String referenceType,
        UUID referenceId,
        String referenceNumber,
        UUID bankId,
        String bankName,
        String bankAccountNumber,
        String accountReference,
        String receivedByName,
        Instant createdAt
) {
    /** Without the reference number or the bank account number (safe default). */
    public static PaymentResponse from(Payment p) {
        return of(p, null, false);
    }

    public static PaymentResponse of(Payment p, String referenceNumber, boolean showBankAccount) {
        return new PaymentResponse(
                p.getId(), p.getMethod().name(), p.getAmount(), p.getBranch().getId(), p.getBranch().getName(),
                p.getReferenceType().name(), p.getReferenceId(), referenceNumber,
                p.getBank() != null ? p.getBank().getId() : null,
                p.getBankName(),
                showBankAccount && p.getBank() != null ? p.getBank().getAccountNumber() : null,
                p.getAccountReference(), p.getReceivedBy().getName(), p.getCreatedAt());
    }
}
