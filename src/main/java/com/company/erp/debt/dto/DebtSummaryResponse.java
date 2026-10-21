package com.company.erp.debt.dto;

import java.math.BigDecimal;

/** Headline numbers for the page: what the business still owes, and how much of that is already overdue. */
public record DebtSummaryResponse(
        BigDecimal totalBorrowed,
        BigDecimal totalPaid,
        BigDecimal totalRemaining,
        BigDecimal overdueRemaining,
        long openCount,
        long overdueCount
) {
}
