package com.company.erp.batch.dto;

import com.company.erp.batch.ProductBatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        String batchNumber,
        UUID productId,
        String productName,
        UUID branchId,
        String branchName,
        int quantity,
        int remainingQuantity,
        BigDecimal costPrice,
        LocalDate receivedDate,
        LocalDate expiryDate,
        Instant createdAt
) {
    /** The same batch with its cost left out, for roles without the cost-price permission. */
    public BatchResponse withoutCost() {
        return new BatchResponse(id, batchNumber, productId, productName, branchId, branchName,
                quantity, remainingQuantity, null, receivedDate, expiryDate, createdAt);
    }

    public static BatchResponse from(ProductBatch batch) {
        return new BatchResponse(
                batch.getId(),
                batch.getBatchNumber(),
                batch.getProduct().getId(),
                batch.getProduct().getName(),
                batch.getBranch().getId(),
                batch.getBranch().getName(),
                batch.getQuantity(),
                batch.getRemainingQuantity(),
                batch.getCostPrice(),
                batch.getReceivedDate(),
                batch.getExpiryDate(),
                batch.getCreatedAt()
        );
    }
}
