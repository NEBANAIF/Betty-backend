package com.company.erp.purchase.dto;

import com.company.erp.purchase.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String orderNumber,
        UUID supplierId,
        String supplierName,
        UUID branchId,
        String branchName,
        LocalDate orderDate,
        String status,
        BigDecimal total,
        String createdByName,
        String approvedByName,
        List<PurchaseOrderItemResponse> items
) {
    public static PurchaseOrderResponse from(PurchaseOrder po) {
        return new PurchaseOrderResponse(
                po.getId(),
                po.getOrderNumber(),
                po.getSupplier().getId(),
                po.getSupplier().getName(),
                po.getBranch().getId(),
                po.getBranch().getName(),
                po.getOrderDate(),
                po.getStatus().name(),
                po.getTotal(),
                po.getCreatedBy().getName(),
                po.getApprovedBy() != null ? po.getApprovedBy().getName() : null,
                po.getItems().stream().map(PurchaseOrderItemResponse::from).toList()
        );
    }
}
