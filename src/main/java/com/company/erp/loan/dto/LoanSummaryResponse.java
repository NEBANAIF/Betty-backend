package com.company.erp.loan.dto;

import com.company.erp.loan.Loan;
import com.company.erp.loan.LoanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Lightweight row for the Loans list view - no payment-history detail. */
public record LoanSummaryResponse(
        UUID id,
        String customerName,
        String saleNumber,
        String branchName,
        BigDecimal originalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String status,
        LocalDate dueDate
) {
    public static LoanSummaryResponse from(Loan loan) {
        LoanStatus effective = LoanResponse.effectiveStatus(loan);
        return new LoanSummaryResponse(
                loan.getId(), loan.getCustomer().getName(), loan.getSale().getSaleNumber(),
                loan.getBranch().getName(), loan.getOriginalAmount(), loan.getPaidAmount(),
                loan.getRemainingAmount(), effective.name(), loan.getDueDate());
    }
}
