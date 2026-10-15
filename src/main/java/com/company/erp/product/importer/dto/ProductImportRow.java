package com.company.erp.product.importer.dto;

/**
 * One data row read from the uploaded .xlsx, before validation - column
 * order matches the template ProductImportTemplateService generates:
 * Name, SKU, Unit, Reorder Level, Description, Branch,
 * Initial Stock, Cost Price. `rowNumber` is the 1-based row as it appears
 * in Excel (header is row 1), so error messages can point the person back
 * at the exact row to fix.
 */
public record ProductImportRow(
        int rowNumber,
        String name,
        String sku,
        String unit,
        String description,
        Integer reorderLevel,
        String branch,
        Integer initialStock,
        java.math.BigDecimal costPrice
) {
}
