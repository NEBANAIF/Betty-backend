package com.company.erp.stockhistory.dto;

import com.company.erp.stockhistory.StockHistory;

import java.time.Instant;
import java.util.UUID;

public record StockHistoryResponse(
        UUID id,
        Instant occurredAt,
        UUID productId,
        String productName,
        UUID branchId,
        String branchName,
        UUID batchId,
        String batchNumber,
        String movementType,
        int quantityChange,
        int previousQuantity,
        int newQuantity,
        String reason,
        UUID userId,
        String userName,
        String referenceType,
        UUID referenceId
) {
    public static StockHistoryResponse from(StockHistory h) {
        return new StockHistoryResponse(
                h.getId(),
                h.getOccurredAt(),
                h.getProduct().getId(),
                h.getProduct().getName(),
                h.getBranch().getId(),
                h.getBranch().getName(),
                h.getBatch() != null ? h.getBatch().getId() : null,
                h.getBatch() != null ? h.getBatch().getBatchNumber() : null,
                h.getMovementType().name(),
                h.getQuantityChange(),
                h.getPreviousQuantity(),
                h.getNewQuantity(),
                h.getReason(),
                h.getUser().getId(),
                h.getUser().getName(),
                h.getReferenceType() != null ? h.getReferenceType().name() : null,
                h.getReferenceId()
        );
    }
}
