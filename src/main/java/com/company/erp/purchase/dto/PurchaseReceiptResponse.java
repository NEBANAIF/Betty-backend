package com.company.erp.purchase.dto;

import com.company.erp.purchase.PurchaseReceipt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One confirmed delivery into the warehouse. */
public record PurchaseReceiptResponse(
        UUID id,
        LocalDate receivedDate,
        String receivedByName,
        String note,
        Instant recordedAt,
        List<Line> lines
) {
    public record Line(String productName, int quantity) {
    }

    public static PurchaseReceiptResponse from(PurchaseReceipt r) {
        return new PurchaseReceiptResponse(r.getId(), r.getReceivedDate(), r.getReceivedBy().getName(), r.getNote(),
                r.getCreatedAt(),
                r.getLines().stream()
                        .map(l -> new Line(l.getPurchaseOrderItem().getProduct().getName(), l.getQuantity()))
                        .toList());
    }
}
