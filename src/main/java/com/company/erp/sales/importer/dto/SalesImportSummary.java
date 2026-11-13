package com.company.erp.sales.importer.dto;

import java.util.List;

/**
 * What Sales > Import returns: a pass/fail line per sale, never all-or-nothing, so one wrong row does not
 * cost the other sales in the file. A refused sale records nothing at all (no stock, payment or loan).
 */
public record SalesImportSummary(
        int totalLines,
        int totalSales,
        int imported,
        int failed,
        int skippedEmptyRows,
        List<SalesImportRowResult> results
) {
}
