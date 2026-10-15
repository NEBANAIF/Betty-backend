package com.company.erp.loan.dto;

import com.company.erp.loan.Loan;
import com.company.erp.loan.LoanStatus;
import com.company.erp.payment.dto.PaymentResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        UUID customerId,
        String customerName,
        UUID saleId,
        String saleNumber,
        UUID branchId,
        String branchName,
        BigDecimal originalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String status,
        LocalDate dueDate,
        Instant createdAt,
        List<PaymentResponse> payments
) {
    public static LoanResponse from(Loan loan, List<PaymentResponse> payments) {
        return new LoanResponse(
                loan.getId(), loan.getCustomer().getId(), loan.getCustomer().getName(),
                loan.getSale().getId(), loan.getSale().getSaleNumber(),
                loan.getBranch().getId(), loan.getBranch().getName(),
                loan.getOriginalAmount(), loan.getPaidAmount(), loan.getRemainingAmount(),
                effectiveStatus(loan).name(), loan.getDueDate(), loan.getCreatedAt(), payments);
    }

    /**
     * The persisted status only ever tracks OPEN/PARTIALLY_PAID/PAID (see
     * LoanStatus). "Overdue" is reported here instead of the raw persisted
     * value whenever the loan still has a balance and its due date has
     * passed - computed fresh on every read, never stored.
     */
    public static LoanStatus effectiveStatus(Loan loan) {
        if (loan.getStatus() != LoanStatus.PAID
                && loan.getDueDate() != null
                && loan.getDueDate().isBefore(LocalDate.now())) {
            return LoanStatus.OVERDUE;
        }
        return loan.getStatus();
    }
}
