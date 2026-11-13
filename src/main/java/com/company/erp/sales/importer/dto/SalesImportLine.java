package com.company.erp.sales.importer.dto;

import java.math.BigDecimal;

/** One product line of the sheet, already read from its cells. {@code error} is set when a cell could not be read. */
public record SalesImportLine(
        int rowNumber,
        String product,
        Integer quantity,
        BigDecimal unitPrice,
        String error
) {
}
