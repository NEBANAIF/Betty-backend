package com.company.erp.product.importer;

import com.company.erp.product.importer.dto.ProductImportSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk-registers products that already exist outside the system (a
 * migration from a spreadsheet, another till, a paper ledger) - fill in
 * the downloaded template, upload it, and each row becomes a product,
 * with an opening stock batch if the row gives a quantity and cost.
 * <p>
 * Gated on both PRODUCT_CREATE and STOCK_ADJUST: a row can both create a
 * product AND write its opening stock, so importing needs whatever a
 * person doing both of those by hand through the UI would need.
 */
@RestController
@RequestMapping("/api/products/import")
@RequiredArgsConstructor
public class ProductImportController {

    private final ProductImportService productImportService;
    private final ProductImportTemplateService productImportTemplateService;

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<byte[]> template() {
        byte[] bytes = productImportTemplateService.build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"product-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE') and hasAuthority('STOCK_ADJUST')")
    public ProductImportSummary importFile(@RequestParam("file") MultipartFile file) {
        return productImportService.importFile(file);
    }
}
