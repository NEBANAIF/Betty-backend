package com.company.erp.purchase.dto;

import com.company.erp.purchase.ShippingMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Replaces the shipment details of a global purchase: the whole route (every stop, in order) and, optionally,
 * the shipping method, shipping cost and expected arrival date. Anything left null keeps its current value.
 */
public record UpdateShipmentRequest(
        ShippingMethod shippingMethod,
        @DecimalMin("0.0") BigDecimal shippingCost,
        LocalDate expectedArrivalDate,
        @Size(max = 10) String currency,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal exchangeRate,
        @NotEmpty @Valid List<ShipmentStageRequest> stages
) {
}
