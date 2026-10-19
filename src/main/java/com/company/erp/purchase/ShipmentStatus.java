package com.company.erp.purchase;

/**
 * Where a global purchase's shipment is. This is only about the journey: it never touches stock. Stock goes up
 * only when someone confirms what was physically counted at the warehouse (PurchaseOrderService.receive).
 * "Delayed" is not stored - it is worked out from the expected arrival date.
 */
public enum ShipmentStatus {
    NOT_SHIPPED,
    IN_TRANSIT,
    ARRIVED
}
