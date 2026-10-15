package com.company.erp.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day's bucket for the sales/revenue/profit trend widgets (spec section 26). */
public record DailyFigureResponse(
        LocalDate date,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        long salesCount
) {
}
