package com.company.erp.sales.importer.dto;

/** Outcome of one sale (one invoice, which may be several rows of the sheet) - one line of the results table. */
public record SalesImportRowResult(
        int rowNumber,
        String invoiceNo,
        int lines,
        boolean success,
        String message
) {
    public static SalesImportRowResult ok(int rowNumber, String invoiceNo, int lines, String message) {
        return new SalesImportRowResult(rowNumber, invoiceNo, lines, true, message);
    }

    public static SalesImportRowResult failed(int rowNumber, String invoiceNo, int lines, String message) {
        return new SalesImportRowResult(rowNumber, invoiceNo, lines, false, message);
    }
}
