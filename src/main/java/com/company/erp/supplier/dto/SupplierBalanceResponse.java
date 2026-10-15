package com.company.erp.supplier.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * totalPaid/outstandingBalance are zero/equal-to-purchased until Payments
 * (Phase 16) exists to record money actually sent to the supplier. This
 * shape is stable now so the Suppliers screen can be built against it.
 */
public record SupplierBalanceResponse(
        UUID supplierId,
        BigDecimal totalPurchased,
        BigDecimal totalPaid,
        BigDecimal outstandingBalance
) {
}
