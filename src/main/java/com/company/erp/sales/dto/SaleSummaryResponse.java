package com.company.erp.sales.dto;

import com.company.erp.sales.Sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lightweight row for the Sales History list view - no item/allocation detail. */
public record SaleSummaryResponse(
        UUID id,
        String saleNumber,
        String branchName,
        String customerName,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String paymentStatus,
        String status,
        Instant createdAt
) {
    public static SaleSummaryResponse from(Sale sale) {
        return new SaleSummaryResponse(
                sale.getId(), sale.getSaleNumber(), sale.getBranch().getName(),
                sale.getCustomer() != null ? sale.getCustomer().getName() : null,
                sale.getTotalAmount(), sale.getPaidAmount(), sale.getRemainingAmount(),
                sale.getPaymentStatus().name(), sale.getStatus().name(), sale.getCreatedAt());
    }
}
