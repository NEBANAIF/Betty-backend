package com.company.erp.sales.importer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One sale to record: all rows that share an Invoice No (or a single row with none). The branch, customer,
 * discount, amount paid and payment method come from the FIRST row; {@code error} carries any problem found
 * while reading those cells.
 */
public record SalesImportSale(
        int firstRowNumber,
        String invoiceNo,
        LocalDate date,
        String branch,
        String customer,
        BigDecimal discount,
        BigDecimal amountPaid,
        String paymentMethod,
        List<SalesImportLine> lines,
        String error
) {
}
