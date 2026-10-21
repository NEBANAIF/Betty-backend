package com.company.erp.debt.dto;

import com.company.erp.debt.Debt;
import com.company.erp.debt.DebtStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** status is the stored one (OPEN / PARTIALLY_PAID / PAID); overdue is computed. payments is empty on list rows. */
public record DebtResponse(
        UUID id,
        String lenderName,
        String debtType,
        String description,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        LocalDate startDate,
        LocalDate dueDate,
        String status,
        boolean overdue,
        String notes,
        /** The purchase order this credit came from, if any (bought on credit). */
        String purchaseOrderNumber,
        String createdByName,
        Instant createdAt,
        List<DebtPaymentResponse> payments
) {
    public static DebtResponse from(Debt d, List<DebtPaymentResponse> payments) {
        boolean overdue = (d.getStatus() == DebtStatus.OPEN || d.getStatus() == DebtStatus.PARTIALLY_PAID) && d.getDueDate() != null
                && d.getDueDate().isBefore(LocalDate.now());
        return new DebtResponse(d.getId(), d.getLenderName(), d.getDebtType().name(), d.getDescription(),
                d.getPrincipalAmount(), d.getInterestAmount(), d.getTotalAmount(), d.getPaidAmount(),
                d.getRemainingAmount(), d.getStartDate(), d.getDueDate(), d.getStatus().name(), overdue,
                d.getNotes(), d.getPurchaseOrder() != null ? d.getPurchaseOrder().getOrderNumber() : null,
                d.getCreatedBy().getName(), d.getCreatedAt(), payments);
    }
}
