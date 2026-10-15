package com.company.erp.transfer.dto;

import com.company.erp.transfer.StockTransfer;

import java.time.Instant;
import java.util.UUID;

/** Lightweight row for the Transfers list view - no item/allocation detail. */
public record TransferSummaryResponse(
        UUID id,
        String transferNumber,
        String sourceBranchName,
        String destinationBranchName,
        String status,
        Instant requestedAt
) {
    public static TransferSummaryResponse from(StockTransfer t) {
        return new TransferSummaryResponse(
                t.getId(), t.getTransferNumber(), t.getSourceBranch().getName(),
                t.getDestinationBranch().getName(), t.getStatus().name(), t.getRequestedAt());
    }
}
