package com.company.erp.returns.customer.dto;

import com.company.erp.returns.customer.CustomerReturnItem;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerReturnItemResponse(
        UUID id,
        UUID saleBatchAllocationId,
        UUID productId,
        String productName,
        String batchNumber,
        int quantityReturned,
        boolean restocked,
        BigDecimal refundAmount
) {
    public static CustomerReturnItemResponse from(CustomerReturnItem item) {
        var allocation = item.getSaleBatchAllocation();
        return new CustomerReturnItemResponse(
                item.getId(), allocation.getId(),
                allocation.getSaleItem().getProduct().getId(),
                allocation.getSaleItem().getProduct().getName(),
                allocation.getBatch().getBatchNumber(),
                item.getQuantityReturned(), item.isRestocked(), item.getRefundAmount());
    }
}
