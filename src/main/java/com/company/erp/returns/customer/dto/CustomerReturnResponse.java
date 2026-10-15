package com.company.erp.returns.customer.dto;

import com.company.erp.returns.customer.CustomerReturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerReturnResponse(
        UUID id,
        UUID saleId,
        String saleNumber,
        UUID customerId,
        String customerName,
        UUID branchId,
        String branchName,
        String status,
        String processedByName,
        BigDecimal refundAmount,
        Instant createdAt,
        List<CustomerReturnItemResponse> items
) {
    public static CustomerReturnResponse from(CustomerReturn r) {
        return new CustomerReturnResponse(
                r.getId(), r.getSale().getId(), r.getSale().getSaleNumber(),
                r.getCustomer() != null ? r.getCustomer().getId() : null,
                r.getCustomer() != null ? r.getCustomer().getName() : null,
                r.getBranch().getId(), r.getBranch().getName(),
                r.getStatus().name(), r.getProcessedBy().getName(), r.getRefundAmount(),
                r.getCreatedAt(), r.getItems().stream().map(CustomerReturnItemResponse::from).toList());
    }
}
