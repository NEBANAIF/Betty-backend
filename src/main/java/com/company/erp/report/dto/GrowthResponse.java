package com.company.erp.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Period-over-period comparison (spec section 27: "Revenue growth",
 * "Profit growth"). previousPeriod is the immediately preceding period of
 * the SAME length as [currentFrom, currentTo] - e.g. a 30-day current
 * range compares against the 30 days right before it.
 */
public record GrowthResponse(
        LocalDate currentFrom,
        LocalDate currentTo,
        LocalDate previousFrom,
        LocalDate previousTo,
        BigDecimal currentValue,
        BigDecimal previousValue,
        BigDecimal growthPercent
) {
}
