package com.company.erp.purchase.dto;

import com.company.erp.purchase.PurchasePaymentType;
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
 * {@code paymentType} left out means PAID. CREDIT records the whole order as owed to the supplier on My Credits &
 * Loans; PARTIAL needs {@code amountPaid} (more than 0, less than the order total, in the order's currency) and
 * records only the balance. {@code creditDueDate} (optional) is when the owed amount has to be paid back.
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
        @Valid List<ShipmentStageRequest> stages,
        PurchasePaymentType paymentType,
        LocalDate creditDueDate,
        @DecimalMin("0.0") BigDecimal amountPaid
) {
    /** The form before the paid / credit choice existed (paymentType left out = fully paid). */
    public CreatePurchaseOrderRequest(UUID supplierId, UUID branchId, LocalDate orderDate,
                                      List<CreatePurchaseOrderItemRequest> items, PurchaseType purchaseType,
                                      String originCountry, String destinationCountry, ShippingMethod shippingMethod,
                                      String currency, BigDecimal exchangeRate, BigDecimal shippingCost,
                                      LocalDate expectedArrivalDate, List<ShipmentStageRequest> stages) {
        this(supplierId, branchId, orderDate, items, purchaseType, originCountry, destinationCountry, shippingMethod,
                currency, exchangeRate, shippingCost, expectedArrivalDate, stages, null, null, null);
    }

    /** The original local-purchase form. */
    public CreatePurchaseOrderRequest(UUID supplierId, UUID branchId, LocalDate orderDate,
                                      List<CreatePurchaseOrderItemRequest> items) {
        this(supplierId, branchId, orderDate, items, PurchaseType.LOCAL, null, null, null, null, null, null, null, null, null, null, null);
    }
}
