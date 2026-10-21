package com.company.erp.purchase.dto;

import com.company.erp.purchase.PurchaseOrder;
import com.company.erp.purchase.ShipmentCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
        List<PurchaseOrderItemResponse> items,
        // ---- global purchases (all null / empty for LOCAL, except purchaseType and receivingStatus) ----
        String purchaseType,
        String originCountry,
        String destinationCountry,
        /** The branch the goods are received into (the destination warehouse for a global order). */
        String destinationWarehouse,
        String shippingMethod,
        String currency,
        BigDecimal exchangeRate,
        BigDecimal shippingCost,
        LocalDate expectedArrivalDate,
        LocalDate actualArrivalDate,
        String shipmentStatus,
        String currentLocation,
        String currentCountry,
        /** "China → Djibouti → Ethiopia → Main Warehouse" */
        String route,
        /** The same route with the recorded dates and status of every stop. */
        String routeDetail,
        Integer totalTransitDays,
        int delayDays,
        boolean delayed,
        List<ShipmentStageResponse> stages,
        /** NOT_RECEIVED, PARTIALLY_RECEIVED or RECEIVED - what has physically been counted into stock. */
        String receivingStatus,
        List<PurchaseReceiptResponse> receipts,
        /** PAID or CREDIT (CREDIT orders are listed on My Credits & Loans). */
        String paymentType,
        /** PARTIAL only: what was paid at order time (order currency); 0 otherwise. */
        BigDecimal amountPaid
) {
    public static PurchaseOrderResponse from(PurchaseOrder po) {
        return from(po, LocalDate.now());
    }

    public static PurchaseOrderResponse from(PurchaseOrder po, LocalDate today) {
        String receiving = po.isFullyReceived() ? "RECEIVED" : po.isPartiallyReceived() ? "PARTIALLY_RECEIVED" : "NOT_RECEIVED";
        List<PurchaseReceiptResponse> receipts = po.getReceipts().stream().map(PurchaseReceiptResponse::from).toList();

        String purchaseType = po.getPurchaseType().name();
        String supplierName = po.getSupplier().getName();

        if (!po.isGlobal()) {
            return new PurchaseOrderResponse(
                    po.getId(), po.getOrderNumber(), po.getSupplier().getId(), supplierName,
                    po.getBranch().getId(), po.getBranch().getName(), po.getOrderDate(), po.getStatus().name(),
                    po.getTotal(), po.getCreatedBy().getName(),
                    po.getApprovedBy() != null ? po.getApprovedBy().getName() : null,
                    po.getItems().stream().map(PurchaseOrderItemResponse::from).toList(),
                    purchaseType, null, null, po.getBranch().getName(), null, null, null, null, null, null,
                    null, null, null, null, null, null, 0, false, List.of(), receiving, receipts, po.getPaymentType().name(), po.getAmountPaid());
        }

        ShipmentCalculator.Summary summary = ShipmentCalculator.summarise(
                po.getStatus(), po.getExpectedArrivalDate(), po.getStages(), today, po.fullyReceivedOn());
        List<ShipmentStageResponse> stages = new ArrayList<>();
        for (int i = 0; i < po.getStages().size(); i++) {
            stages.add(ShipmentStageResponse.from(po.getStages().get(i), summary.stageMetrics().get(i)));
        }

        return new PurchaseOrderResponse(
                po.getId(), po.getOrderNumber(), po.getSupplier().getId(), supplierName,
                po.getBranch().getId(), po.getBranch().getName(), po.getOrderDate(), po.getStatus().name(),
                po.getTotal(), po.getCreatedBy().getName(),
                po.getApprovedBy() != null ? po.getApprovedBy().getName() : null,
                po.getItems().stream().map(PurchaseOrderItemResponse::from).toList(),
                purchaseType, po.getOriginCountry(), po.getDestinationCountry(), po.getBranch().getName(),
                po.getShippingMethod() != null ? po.getShippingMethod().name() : null,
                po.getCurrency(), po.getExchangeRate(), po.getShippingCost(), po.getExpectedArrivalDate(),
                summary.actualArrivalDate(), summary.status().name(), summary.currentLocation(),
                summary.currentCountry(), ShipmentCalculator.route(po.getStages()),
                ShipmentCalculator.routeWithDates(po.getStages()), summary.totalTransitDays(),
                summary.delayDays(), summary.delayed(), stages, receiving, receipts, po.getPaymentType().name(), po.getAmountPaid());
    }
}
