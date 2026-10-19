package com.company.erp.purchase.dto;

import com.company.erp.purchase.PurchaseType;
import com.company.erp.purchase.ShippingMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A LOCAL order needs only the first four fields (exactly as before). A GLOBAL order also needs the origin and
 * destination country, shipping method, currency, expected arrival date and a route of at least two stops; the
 * receiving branch ({@code branchId}) is the destination warehouse and must be a warehouse.
 * {@code purchaseType} left out means LOCAL, so older callers keep working.
 */
public record CreatePurchaseOrderRequest(
        @NotNull UUID supplierId,
        @NotNull UUID branchId,
        @NotNull LocalDate orderDate,
        @NotEmpty @Valid List<CreatePurchaseOrderItemRequest> items,
        PurchaseType purchaseType,
        @Size(max = 100) String originCountry,
        @Size(max = 100) String destinationCountry,
        ShippingMethod shippingMethod,
        @Size(max = 10) String currency,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal exchangeRate,
        @DecimalMin("0.0") BigDecimal shippingCost,
        LocalDate expectedArrivalDate,
        @Valid List<ShipmentStageRequest> stages
) {
    /** The original local-purchase form. */
    public CreatePurchaseOrderRequest(UUID supplierId, UUID branchId, LocalDate orderDate,
                                      List<CreatePurchaseOrderItemRequest> items) {
        this(supplierId, branchId, orderDate, items, PurchaseType.LOCAL, null, null, null, null, null, null, null, null);
    }
}
