package com.company.erp.sales.importer;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the .xlsx a person downloads from Sales History > Import sales. The column order is the contract
 * {@link SalesImportService} parses against (COL_* constants) - if a column moves, they move with it.
 * Also lists the branches that already exist, as a lookup for the Branch column.
 */
@Service
@RequiredArgsConstructor
public class SalesImportTemplateService {

    private static final String[] HEADERS = {
            "Date", "Invoice No", "Branch", "Customer", "Product (SKU or name)",
            "Quantity", "Unit Price", "Discount", "Amount Paid", "Payment Method",
    };

    private static final String[] HOW_TO_FILL = {
            "Read me before you upload",
            "One row = one product line. Rows with the SAME Invoice No form ONE sale with several lines; a row with no Invoice No is a sale of its own.",
            "Date: the day of the sale, written 2025-03-04 (or day/month/year, or a real Excel date). It cannot be in the future.",
            "Branch, Customer, Discount, Amount Paid and Payment Method are read from the FIRST row of each invoice; leave them blank on the other lines.",
            "Branch: a name or code from the Branches sheet. Leave it blank to use the branch you pick on the import screen.",
            "Product: the SKU, or the exact product name. The product must already exist (Inventory > Products).",
            "Customer: a customer's name. A name that does not exist yet is added as a new customer. Required whenever the sale is not paid in full.",
            "Discount: the discount on the whole invoice (not per line). Blank = none.",
            "Amount Paid: what the customer paid. BLANK MEANS PAID IN FULL. Write 0 for a sale on credit. Any unpaid part becomes a customer loan.",
            "Payment Method: Cash or Bank (blank = Cash). For Bank, choose the bank on the import screen.",
            "Stock: each line takes its quantity out of the branch's stock (oldest batch first), so the stock must be entered first. A sale without enough stock is refused and nothing of it is recorded.",
            "Sales are processed from the oldest date to the newest. An Invoice No that already exists is refused, so uploading the same file twice does not duplicate sales. (Rows WITHOUT an Invoice No cannot be recognised, so upload such a file only once.)",
            "Delete the three example rows before uploading.",
    };

    private final BranchRepository branchRepository;

    public byte[] build() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            Sheet sales = wb.createSheet(SalesImportService.SHEET);
            Row header = sales.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                header.getCell(i).setCellStyle(head);
                sales.setColumnWidth(i, i == 4 ? 9000 : 4200);
            }
            sales.setColumnWidth(0, 3600);
            sales.createFreezePane(0, 1);

            List<Branch> branches = branchRepository.findByActiveTrue().stream()
                    .sorted(Comparator.comparing(Branch::getName, String.CASE_INSENSITIVE_ORDER)).toList();
            String branchName = branches.isEmpty() ? "Main Store" : branches.get(0).getName();
            LocalDate day = LocalDate.now().minusDays(1);
            example(sales, 1, dateStyle, day, "INV-1001", branchName, "Abebe Kebede", "Sample product A", 10, 120, 50, 1150, "Cash");
            example(sales, 2, dateStyle, day, null, null, null, "Sample product B", 5, 300, null, null, null);
            example(sales, 3, dateStyle, day, null, branchName, null, "Sample product C", 1, 85.5, null, 0, null);

            Sheet branchSheet = wb.createSheet("Branches");
            Row bh = branchSheet.createRow(0);
            bh.createCell(0).setCellValue("Existing branch names");
            bh.createCell(1).setCellValue("Code");
            bh.getCell(0).setCellStyle(head);
            bh.getCell(1).setCellStyle(head);
            int r = 1;
            for (Branch b : branches) {
                Row row = branchSheet.createRow(r++);
                row.createCell(0).setCellValue(b.getName());
                row.createCell(1).setCellValue(b.getCode());
            }
            branchSheet.setColumnWidth(0, 9000);
            branchSheet.setColumnWidth(1, 4200);

            Sheet how = wb.createSheet("How to fill");
            for (int i = 0; i < HOW_TO_FILL.length; i++) {
                Row row = how.createRow(i);
                row.createCell(0).setCellValue(HOW_TO_FILL[i]);
                if (i == 0) {
                    row.getCell(0).setCellStyle(head);
                }
            }
            how.setColumnWidth(0, 32000);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void example(Sheet sheet, int rowIndex, CellStyle dateStyle, LocalDate date, String invoice, String branch,
                         String customer, String product, int qty, double price, Integer discount, Integer paid, String method) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(java.sql.Date.valueOf(date));
        row.getCell(0).setCellStyle(dateStyle);
        if (invoice != null) row.createCell(1).setCellValue(invoice);
        if (branch != null) row.createCell(2).setCellValue(branch);
        if (customer != null) row.createCell(3).setCellValue(customer);
        row.createCell(4).setCellValue(product);
        row.createCell(5).setCellValue(qty);
        row.createCell(6).setCellValue(price);
        if (discount != null) row.createCell(7).setCellValue(discount);
        if (paid != null) row.createCell(8).setCellValue(paid);
        if (method != null) row.createCell(9).setCellValue(method);
    }
}
