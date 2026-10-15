package com.company.erp.product.importer;

import com.company.erp.branch.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Builds the .xlsx a person downloads from Products > Import Existing Data,
 * fills in, and re-uploads. Column order here is the CONTRACT
 * ProductImportService parses against - if these columns move, its
 * COL_* constants have to move with them.
 * <p>
 * Also lists the branches that already exist, on their own sheet, purely
 * as a lookup reference: Branch on the Products sheet must match one of
 * these names (or codes) exactly (case-insensitive) or that row is
 * rejected at import time.
 */
@Service
@RequiredArgsConstructor
public class ProductImportTemplateService {

    private static final String[] HEADERS = {
            "Name", "SKU", "Unit", "Reorder Level", "Description",
            "Branch", "Initial Stock", "Cost Price",
    };

    private final BranchRepository branchRepository;

    public byte[] build() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);

            buildProductsSheet(workbook, headerStyle);
            buildReferenceSheet(workbook, headerStyle, "Branches",
                    new String[]{"Existing branch names", "Code"},
                    branchRepository.findByActiveTrue().stream().map(b -> new String[]{b.getName(), b.getCode()}).toList());

            buildReferenceSheet(workbook, headerStyle, "How to fill",
                    new String[]{"Read me before you upload"},
                    java.util.List.of(
                            new String[]{"One row = one product AT one branch."},
                            new String[]{"The same product in two branches (e.g. Power Cable in Warehouse A and Warehouse B): "
                                    + "repeat the SAME Name and SAME SKU on two rows - one per branch - each with its own Branch, Initial Stock and Cost Price."},
                            new String[]{"The product is created once; every row adds that branch's opening stock. Do NOT invent a different SKU for the same product - it would become two separate products."},
                            new String[]{"If the SKU already exists in the system, the Name must match it; the existing product's Unit, Reorder Level and Description are kept."},
                            new String[]{"The same SKU + Branch twice in one file is rejected, so stock isn't added twice."},
                            new String[]{"Delete the two example rows on the Products sheet before uploading."}));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void buildProductsSheet(XSSFWorkbook workbook, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Products");

        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        // One filled-in example row so the format (and that Branch
        // is a name, not an ID) is obvious without reading instructions.
        // Existing-data columns only: Branch/Initial Stock/Cost Price stay
        // blank for a product with no opening stock to record.
        writeExampleRow(sheet.createRow(1),
                "Bottled Water 500ml", "BW-500", "pcs", 20,
                "Case of 24", "Main Warehouse", 150, "8.50");
        writeExampleRow(sheet.createRow(2),
                "Notebook A5", "NB-A5", "pcs", 10,
                "", "", "", "");

        for (int i = 0; i < HEADERS.length; i++) {
            sheet.setColumnWidth(i, 20 * 256);
        }
        sheet.setColumnWidth(4, 30 * 256);
    }

    private void writeExampleRow(Row row, String name, String sku, String unit,
                                  int reorderLevel, String description, String branch, Object initialStock,
                                  String costPrice) {
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(sku);
        row.createCell(2).setCellValue(unit);
        row.createCell(3).setCellValue(reorderLevel);
        row.createCell(4).setCellValue(description);
        row.createCell(5).setCellValue(branch);
        row.createCell(6).setCellValue(String.valueOf(initialStock));
        row.createCell(7).setCellValue(costPrice);
    }

    private void buildReferenceSheet(XSSFWorkbook workbook, CellStyle headerStyle, String sheetName,
                                      String[] headers, java.util.List<String[]> rows) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int r = 1;
        for (String[] values : rows) {
            Row row = sheet.createRow(r++);
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i]);
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, 24 * 256);
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
