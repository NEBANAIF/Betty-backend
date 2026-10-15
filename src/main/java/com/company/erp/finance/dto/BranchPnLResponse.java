package com.company.erp.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Spec section 24's exact formula set:
 * revenue      = gross sales revenue - refunded revenue
 * cogs         = gross COGS - reversed COGS (restocked returns only)
 * grossProfit  = revenue - cogs
 * netProfit    = grossProfit - totalExpenses
 * profitMargin = netProfit / revenue * 100 (zero when revenue is zero,
 *                never a divide-by-zero)
 */
public record BranchPnLResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal totalExpenses,
        BigDecimal netProfit,
        BigDecimal profitMarginPercent
) {
}
