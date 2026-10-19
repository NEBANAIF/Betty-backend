package com.company.erp.purchase.dto;

import com.company.erp.purchase.PurchaseShipmentStage;
import com.company.erp.purchase.ShipmentCalculator;

import java.time.LocalDate;
import java.util.UUID;

public record ShipmentStageResponse(
        UUID id,
        int stageOrder,
        String country,
        String location,
        LocalDate arrivalDate,
        LocalDate departureDate,
        String status,
        /** Days the goods spent (or have spent so far) at this stop; null if they have not been there. */
        Integer daysAtStage,
        /** True while the goods are still at this stop, so {@code daysAtStage} grows every day. */
        boolean ongoing,
        String notes
) {
    public static ShipmentStageResponse from(PurchaseShipmentStage s, ShipmentCalculator.StageMetrics m) {
        return new ShipmentStageResponse(s.getId(), s.getStageOrder(), s.getCountry(), s.getLocation(),
                s.getArrivalDate(), s.getDepartureDate(), s.getStatus().name(),
                m.daysAtStage(), m.ongoing(), s.getNotes());
    }
}
