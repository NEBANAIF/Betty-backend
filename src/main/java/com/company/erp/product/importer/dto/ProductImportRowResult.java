package com.company.erp.product.importer.dto;

/** Outcome of importing one row - shown to the person as one line of the results table. */
public record ProductImportRowResult(
        int rowNumber,
        String sku,
        boolean success,
        String message
) {
    public static ProductImportRowResult ok(int rowNumber, String sku, String message) {
        return new ProductImportRowResult(rowNumber, sku, true, message);
    }

    public static ProductImportRowResult failed(int rowNumber, String sku, String message) {
        return new ProductImportRowResult(rowNumber, sku, false, message);
    }
}
