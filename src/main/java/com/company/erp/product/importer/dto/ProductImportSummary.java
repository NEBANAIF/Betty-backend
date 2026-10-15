package com.company.erp.product.importer.dto;

import java.util.List;

/**
 * What Products > Import returns after processing every row in the
 * uploaded file - a per-row pass/fail breakdown, not an all-or-nothing
 * result, so one typo in row 14 doesn't cost the other 199 rows.
 */
public record ProductImportSummary(
        int totalRows,
        int created,
        int failed,
        List<ProductImportRowResult> rows
) {
}
