package com.company.erp.purchase;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Everything the Purchases list (and its export) can be filtered by. Any field left null is ignored.
 * {@code shipmentStatus} is NOT_SHIPPED, IN_TRANSIT, ARRIVED or DELAYED (late against the expected arrival date).
 * {@code arrivalMonth}/{@code arrivalYear} look at the actual arrival date, or the expected one until it arrives.
 */
public record PurchaseSearchCriteria(
        UUID supplierId,
        PurchaseOrderStatus status,
        String search,
        UUID productId,
        LocalDate from,
        LocalDate to,
        PurchaseType purchaseType,
        String originCountry,
        String destinationCountry,
        String currentCountry,
        UUID warehouseId,
        String shipmentStatus,
        Integer arrivalMonth,
        Integer arrivalYear
) {
    public static PurchaseSearchCriteria none() {
        return new PurchaseSearchCriteria(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }
}
