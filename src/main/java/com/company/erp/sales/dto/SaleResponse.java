package com.company.erp.sales.dto;

import com.company.erp.sales.Sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * cogs and grossProfit are computed here from the item-level allocations
 * (never stored on Sale itself) - this is the response shape the Phase 1
 * acceptance test's numbers (revenue 3,000 / COGS 2,200 / gross profit
 * 800) come out of end to end.
 */
public record SaleResponse(
        UUID id,
        String saleNumber,
        UUID branchId,
        String branchName,
        UUID customerId,
        String customerName,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String paymentStatus,
        String status,
        String voidReason,
        BigDecimal cogs,
        BigDecimal grossProfit,
        String createdByName,
        Instant createdAt,
        List<SaleItemResponse> items
) {
    /** The same sale with cost of goods, profit and per-batch costs left out (roles without VIEW_PROFIT). */
    public SaleResponse withoutProfit() {
        return new SaleResponse(id, saleNumber, branchId, branchName, customerId, customerName,
                totalAmount, discountAmount, paidAmount, remainingAmount, paymentStatus, status, voidReason,
                null, null, createdByName, createdAt,
                items.stream().map(SaleItemResponse::withoutCost).toList());
    }

    public static SaleResponse from(Sale sale) {
        List<SaleItemResponse> items = sale.getItems().stream().map(SaleItemResponse::from).toList();
        BigDecimal cogs = items.stream().map(SaleItemResponse::cogs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossProfit = sale.getTotalAmount().subtract(cogs);

        return new SaleResponse(
                sale.getId(), sale.getSaleNumber(), sale.getBranch().getId(), sale.getBranch().getName(),
                sale.getCustomer() != null ? sale.getCustomer().getId() : null,
                sale.getCustomer() != null ? sale.getCustomer().getName() : null,
                sale.getTotalAmount(), sale.getDiscountAmount(), sale.getPaidAmount(), sale.getRemainingAmount(),
                sale.getPaymentStatus().name(), sale.getStatus().name(), sale.getVoidReason(), cogs, grossProfit,
                sale.getCreatedBy().getName(), sale.getCreatedAt(), items);
    }
}
