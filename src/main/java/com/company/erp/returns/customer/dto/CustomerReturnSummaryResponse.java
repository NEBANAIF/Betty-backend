package com.company.erp.returns.customer.dto;

import com.company.erp.returns.customer.CustomerReturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerReturnSummaryResponse(
        UUID id,
        String saleNumber,
        String customerName,
        String branchName,
        String status,
        BigDecimal refundAmount,
        Instant createdAt
) {
    public static CustomerReturnSummaryResponse from(CustomerReturn r) {
        return new CustomerReturnSummaryResponse(
                r.getId(), r.getSale().getSaleNumber(),
                r.getCustomer() != null ? r.getCustomer().getName() : "Walk-in",
                r.getBranch().getName(), r.getStatus().name(), r.getRefundAmount(), r.getCreatedAt());
    }
}
