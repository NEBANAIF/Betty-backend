package com.company.erp.stockchange.dto;

import com.company.erp.stockchange.StockChangeRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StockChangeResponse(
        UUID id,
        String changeType,
        String status,
        UUID productId,
        String productName,
        String sku,
        String unit,
        UUID branchId,
        String branchName,
        int quantity,
        /** Only for someone who may see costs, or who entered it themselves. */
        BigDecimal costPrice,
        LocalDate receivedDate,
        LocalDate expiryDate,
        String movementType,
        UUID batchId,
        String batchNumber,
        String note,
        String requestedByName,
        Instant requestedAt,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote,
        /** Entered by the signed-in user (they may discard it even without approval rights). */
        boolean mine
) {
    public static StockChangeResponse from(StockChangeRequest r, boolean showCost, boolean mine) {
        return new StockChangeResponse(
                r.getId(), r.getChangeType().name(), r.getStatus().name(),
                r.getProduct().getId(), r.getProduct().getName(), r.getProduct().getSku(), r.getProduct().getUnit(),
                r.getBranch().getId(), r.getBranch().getName(),
                r.getQuantity(),
                showCost || mine ? r.getCostPrice() : null,
                r.getReceivedDate(),
                r.getExpiryDate(),
                r.getMovementType() != null ? r.getMovementType().name() : null,
                r.getBatch() != null ? r.getBatch().getId() : null,
                r.getBatch() != null ? r.getBatch().getBatchNumber() : null,
                r.getNote(),
                r.getRequestedBy().getName(), r.getCreatedAt(),
                r.getReviewedBy() != null ? r.getReviewedBy().getName() : null,
                r.getReviewedAt(), r.getReviewNote(), mine);
    }
}
