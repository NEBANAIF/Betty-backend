package com.company.erp.sales.dto;

import com.company.erp.sales.SaleItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * cogs is SUM(allocation.lineCost) - always derived from the real batch
 * costs recorded at sale time, never from the product's current price.
 */
public record SaleItemResponse(
        UUID productId,
        String productName,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        BigDecimal cogs,
        List<SaleBatchAllocationResponse> allocations
) {
    public SaleItemResponse withoutCost() {
        return new SaleItemResponse(productId, productName, sku, quantity, unitPrice, lineTotal, null,
                allocations.stream().map(SaleBatchAllocationResponse::withoutCost).toList());
    }

    public static SaleItemResponse from(SaleItem item) {
        BigDecimal cogs = item.getAllocations().stream()
                .map(a -> a.getUnitCost().multiply(BigDecimal.valueOf(a.getQuantityAllocated())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SaleItemResponse(
                item.getProduct().getId(), item.getProduct().getName(), item.getProduct().getSku(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal(), cogs,
                item.getAllocations().stream().map(SaleBatchAllocationResponse::from).toList());
    }
}
