package com.company.erp.product.importer;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.company.erp.common.exception.ErpException;
import com.company.erp.product.importer.dto.ProductImportRow;
import com.company.erp.product.importer.dto.ProductImportRowResult;
import com.company.erp.product.importer.dto.ProductImportSummary;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Reads the uploaded product-import workbook (see ProductImportTemplateService
 * for the column layout it expects) and creates one product - with an
 * opening stock batch, if the row has one - per data row.
 * <p>
 * Deliberately NOT @Transactional: each row is imported by a separate call
 * to ProductImportRowService (a distinct Spring bean, so its own
 * @Transactional actually takes effect), so a bad row fails on its own
 * without rolling back every row already committed before it.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    /** Name, SKU, Unit, Reorder Level, Description, Branch, Initial Stock, Cost Price. */
    private static final int COL_NAME = 0;
    private static final int COL_SKU = 1;
    private static final int COL_UNIT = 2;
    private static final int COL_REORDER_LEVEL = 3;
    private static final int COL_DESCRIPTION = 4;
    private static final int COL_BRANCH = 5;
    private static final int COL_INITIAL_STOCK = 6;
    private static final int COL_COST_PRICE = 7;

    private final ProductImportRowService productImportRowService;

    public ProductImportSummary importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleViolationException("Choose a file to import");
        }

        List<ProductImportRow> rows;
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            rows = readRows(workbook);
        } catch (IOException e) {
            throw new BusinessRuleViolationException(
                    "Couldn't read that file - make sure it's the .xlsx template, not edited into another format");
        }

        if (rows.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "No data rows found - fill in the template below the header row and try again");
        }

        List<ProductImportRowResult> results = new ArrayList<>();
        // SKU + branch already seen in this file: a second row for the same pair would add the stock twice.
        Set<String> seen = new HashSet<>();
        int created = 0;
        for (ProductImportRow row : rows) {
            String pair = row.sku() == null || row.sku().isBlank() || row.branch() == null || row.branch().isBlank()
                    ? null
                    : row.sku().trim().toLowerCase() + "|" + row.branch().trim().toLowerCase();
            if (pair != null && !seen.add(pair)) {
                results.add(ProductImportRowResult.failed(row.rowNumber(), row.sku(),
                        "Repeated: SKU '" + row.sku().trim() + "' for branch '" + row.branch().trim()
                                + "' is already in this file - row skipped so the stock isn't added twice"));
                continue;
            }
            try {
                String message = productImportRowService.importRow(row);
                results.add(ProductImportRowResult.ok(row.rowNumber(), row.sku(), message));
                created++;
            } catch (ErpException e) {
                results.add(ProductImportRowResult.failed(row.rowNumber(), row.sku(), e.getMessage()));
            } catch (Exception e) {
                results.add(ProductImportRowResult.failed(row.rowNumber(), row.sku(), "Unexpected error: " + e.getMessage()));
            }
        }

        return new ProductImportSummary(rows.size(), created, rows.size() - created, results);
    }

    private List<ProductImportRow> readRows(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        List<ProductImportRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlank(row)) {
                continue;
            }
            rows.add(new ProductImportRow(
                    i + 1,
                    stringCell(row, COL_NAME),
                    stringCell(row, COL_SKU),
                    stringCell(row, COL_UNIT),
                    stringCell(row, COL_DESCRIPTION),
                    intCell(row, COL_REORDER_LEVEL),
                    stringCell(row, COL_BRANCH),
                    intCell(row, COL_INITIAL_STOCK),
                    decimalCell(row, COL_COST_PRICE)));
        }
        return rows;
    }

    private boolean isBlank(Row row) {
        for (int c = 0; c <= COL_COST_PRICE; c++) {
            if (!cellText(row.getCell(c)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String stringCell(Row row, int col) {
        String text = cellText(row.getCell(col));
        return text.isBlank() ? null : text;
    }

    private Integer intCell(Row row, int col) {
        String text = cellText(row.getCell(col));
        if (text.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            throw new BusinessRuleViolationException("'" + text + "' isn't a whole number");
        }
    }

    private BigDecimal decimalCell(Row row, int col) {
        String text = cellText(row.getCell(col));
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new BusinessRuleViolationException("'" + text + "' isn't a number");
        }
    }

    /** Reads any cell type as text so a person typing "10" or Excel auto-formatting a number both work. */
    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value == Math.floor(value) && !Double.isInfinite(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return cellText0FromFormula(cell);
        }
        return cell.toString().trim();
    }

    private String cellText0FromFormula(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                case STRING -> cell.getStringCellValue().trim();
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
