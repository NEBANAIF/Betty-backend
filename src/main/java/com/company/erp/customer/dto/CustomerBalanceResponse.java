package com.company.erp.customer.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Unlike SupplierBalanceResponse (which is still a placeholder pending
 * Phase 16), this is real from Phase 7 onward: Loan exists now, so
 * totalBorrowed/outstandingBalance are true SUM()s over the customer's
 * loans, not stand-ins.
 */
public record CustomerBalanceResponse(
        UUID customerId,
        BigDecimal totalBorrowed,
        BigDecimal totalPaid,
        BigDecimal outstandingBalance
) {
    public static CustomerBalanceResponse of(UUID customerId, BigDecimal totalBorrowed, BigDecimal outstanding) {
        return new CustomerBalanceResponse(customerId, totalBorrowed, totalBorrowed.subtract(outstanding), outstanding);
    }
}
