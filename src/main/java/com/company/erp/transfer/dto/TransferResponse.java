package com.company.erp.transfer.dto;

import com.company.erp.transfer.StockTransfer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String transferNumber,
        UUID sourceBranchId,
        String sourceBranchName,
        UUID destinationBranchId,
        String destinationBranchName,
        String status,
        String requestedByName,
        String approvedByName,
        String receivedByName,
        Instant requestedAt,
        Instant approvedAt,
        Instant dispatchedAt,
        Instant receivedAt,
        String rejectionReason,
        List<TransferItemResponse> items
) {
    public static TransferResponse from(StockTransfer t) {
        return new TransferResponse(
                t.getId(), t.getTransferNumber(),
                t.getSourceBranch().getId(), t.getSourceBranch().getName(),
                t.getDestinationBranch().getId(), t.getDestinationBranch().getName(),
                t.getStatus().name(),
                t.getRequestedBy().getName(),
                t.getApprovedBy() != null ? t.getApprovedBy().getName() : null,
                t.getReceivedBy() != null ? t.getReceivedBy().getName() : null,
                t.getRequestedAt(), t.getApprovedAt(), t.getDispatchedAt(), t.getReceivedAt(),
                t.getRejectionReason(),
                t.getItems().stream().map(TransferItemResponse::from).toList());
    }
}
