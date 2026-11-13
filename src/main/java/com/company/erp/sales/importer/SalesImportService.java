package com.company.erp.sales.importer;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ErpException;
import com.company.erp.sales.importer.dto.SalesImportLine;
import com.company.erp.sales.importer.dto.SalesImportRowResult;
import com.company.erp.sales.importer.dto.SalesImportSale;
import com.company.erp.sales.importer.dto.SalesImportSummary;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the uploaded sales workbook (columns: see {@link SalesImportTemplateService}) and records one sale per
 * invoice. Rows with the same Invoice No are the lines of ONE sale; a row without an Invoice No is a sale of its own.
 * Sales are recorded from the oldest date to the newest, each in its own transaction
 * ({@link SalesImportRowService}), so a refused sale leaves nothing behind and does not stop the others.
 */
@Service
@RequiredArgsConstructor
public class SalesImportService {

    static final String SHEET = "Sales";
    private static final int COL_DATE = 0;
    private static final int COL_INVOICE = 1;
    private static final int COL_BRANCH = 2;
    private static final int COL_CUSTOMER = 3;
    private static final int COL_PRODUCT = 4;
    private static final int COL_QUANTITY = 5;
    private static final int COL_PRICE = 6;
    private static final int COL_DISCOUNT = 7;
    private static final int COL_PAID = 8;
    private static final int COL_METHOD = 9;
    private static final int LAST_COL = COL_METHOD;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu-M-d"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("uuuu/M/d"));

    private final SalesImportRowService rowService;

    public SalesImportSummary importFile(MultipartFile file, UUID defaultBranchId, UUID bankId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleViolationException("Choose a file to import");
        }

        List<SalesImportSale> sales;
        int[] counters = new int[2]; // [0] data lines read, [1] empty rows skipped
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            sales = readSales(workbook, counters);
        } catch (IOException e) {
            throw new BusinessRuleViolationException(
                    "Couldn't read that file - make sure it's the .xlsx template, not edited into another format");
        }
        if (sales.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "No data rows found - fill in the template below the header row and try again");
        }

        // Oldest first, so stock is taken in the order things really happened.
        sales.sort(Comparator
                .comparing(SalesImportSale::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(SalesImportSale::firstRowNumber));

        List<SalesImportRowResult> results = new ArrayList<>();
        int imported = 0;
        for (SalesImportSale sale : sales) {
            try {
                String message = rowService.importSale(sale, defaultBranchId, bankId);
                results.add(SalesImportRowResult.ok(sale.firstRowNumber(), sale.invoiceNo(), sale.lines().size(), message));
                imported++;
            } catch (ErpException e) {
                results.add(SalesImportRowResult.failed(sale.firstRowNumber(), sale.invoiceNo(), sale.lines().size(), e.getMessage()));
            } catch (Exception e) {
                results.add(SalesImportRowResult.failed(sale.firstRowNumber(), sale.invoiceNo(), sale.lines().size(),
                        "Unexpected error: " + e.getMessage()));
            }
        }
        // Show the results in sheet order, which is how the person will look for them.
        results.sort(Comparator.comparingInt(SalesImportRowResult::rowNumber));
        return new SalesImportSummary(counters[0], sales.size(), imported, sales.size() - imported, counters[1], results);
    }

    // ------------------------------------------------------------------ reading the sheet

    private List<SalesImportSale> readSales(Workbook workbook, int[] counters) {
        Sheet sheet = workbook.getSheet(SHEET);
        if (sheet == null) {
            sheet = workbook.getSheetAt(0);
        }
        // key -> sale under construction; a row without an invoice number gets a key of its own.
        Map<String, Builder> groups = new LinkedHashMap<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlank(row)) {
                continue;
            }
            // A row with a date (or nothing but formatting) and no product, quantity or price is an empty line.
            if (text(row, COL_PRODUCT).isBlank() && text(row, COL_QUANTITY).isBlank() && text(row, COL_PRICE).isBlank()
                    && text(row, COL_INVOICE).isBlank() && text(row, COL_CUSTOMER).isBlank()) {
                counters[1]++;
                continue;
            }
            counters[0]++;
            int rowNumber = i + 1;
            String invoice = text(row, COL_INVOICE);
            String key = invoice.isBlank() ? "row-" + rowNumber : "inv-" + invoice.toLowerCase(Locale.ROOT);
            Builder builder = groups.get(key);
            if (builder == null) {
                builder = new Builder(rowNumber, invoice.isBlank() ? null : invoice);
                groups.put(key, builder);
                builder.readHeader(row);
            }
            builder.lines.add(readLine(row, rowNumber));
        }
        List<SalesImportSale> sales = new ArrayList<>();
        groups.values().forEach(b -> sales.add(b.build()));
        return sales;
    }

    /** Collects the first row's sale-level cells and every row's product line. */
    private final class Builder {
        final int firstRow;
        final String invoice;
        final List<SalesImportLine> lines = new ArrayList<>();
        LocalDate date;
        String branch;
        String customer;
        BigDecimal discount;
        BigDecimal paid;
        String method;
        String error;

        Builder(int firstRow, String invoice) {
            this.firstRow = firstRow;
            this.invoice = invoice;
        }

        void readHeader(Row row) {
            try {
                date = dateCell(row, COL_DATE);
                branch = blankToNull(text(row, COL_BRANCH));
                customer = blankToNull(text(row, COL_CUSTOMER));
                method = blankToNull(text(row, COL_METHOD));
                discount = moneyCell(row, COL_DISCOUNT, "Discount");
                paid = moneyCell(row, COL_PAID, "Amount Paid");
            } catch (BusinessRuleViolationException e) {
                error = e.getMessage();
            }
        }

        SalesImportSale build() {
            return new SalesImportSale(firstRow, invoice, date, branch, customer, discount, paid, method, lines, error);
        }
    }

    private SalesImportLine readLine(Row row, int rowNumber) {
        String product = text(row, COL_PRODUCT);
        try {
            String q = text(row, COL_QUANTITY);
            Integer quantity = null;
            if (!q.isBlank()) {
                BigDecimal value = parseNumber(q, "Quantity");
                if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) {
                    throw new BusinessRuleViolationException("Quantity must be a whole number above 0");
                }
                quantity = value.intValueExact();
            }
            BigDecimal price = moneyCell(row, COL_PRICE, "Unit Price");
            return new SalesImportLine(rowNumber, product, quantity, price, null);
        } catch (BusinessRuleViolationException | ArithmeticException e) {
            return new SalesImportLine(rowNumber, product, null, null,
                    e instanceof ArithmeticException ? "Quantity must be a whole number above 0" : e.getMessage());
        }
    }

    private boolean isBlank(Row row) {
        for (int c = 0; c <= LAST_COL; c++) {
            if (!text(row, c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private LocalDate dateCell(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        }
        String raw = text(row, col);
        if (raw.isBlank()) {
            return null;
        }
        // "2025-03-04 00:00:00" -> keep the date part
        String datePart = raw.contains(" ") ? raw.substring(0, raw.indexOf(' ')) : raw;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(datePart, f);
            } catch (DateTimeParseException ignored) {
                // try the next layout
            }
        }
        throw new BusinessRuleViolationException("'" + raw + "' isn't a date - write it like 2025-03-04");
    }

    private BigDecimal moneyCell(Row row, int col, String label) {
        String t = text(row, col);
        if (t.isBlank()) {
            return null;
        }
        BigDecimal v = parseNumber(t, label);
        if (v.signum() < 0) {
            throw new BusinessRuleViolationException(label + " must be a number, 0 or more");
        }
        return v;
    }

    private BigDecimal parseNumber(String text, String label) {
        try {
            return new BigDecimal(text.replace(",", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            throw new BusinessRuleViolationException(label + " must be a number, 0 or more");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Any cell as trimmed text, so numbers typed as text and text-looking numbers both read the same. */
    private String text(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        try {
            if (type == CellType.NUMERIC) {
                if (col == COL_DATE) {
                    return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate().toString();
                }
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value) && !Double.isInfinite(value)) {
                    return String.valueOf((long) value);
                }
                return BigDecimal.valueOf(value).toPlainString();
            }
            if (type == CellType.STRING) {
                return cell.getStringCellValue().trim();
            }
            if (type == CellType.BOOLEAN) {
                return String.valueOf(cell.getBooleanCellValue());
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }
}
