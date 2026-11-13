package com.company.erp.sales.importer;

import com.company.erp.sales.importer.dto.SalesImportSummary;
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

import java.util.UUID;

/**
 * Bulk-records sales that already happened (from a spreadsheet, a paper book, another till).
 * Needs POS_ACCESS - the same permission as ringing a sale up by hand - and each sale is still checked
 * against the caller's branch access, discount permission and stock inside the normal checkout.
 */
@RestController
@RequestMapping("/api/sales/import")
@RequiredArgsConstructor
public class SalesImportController {

    private final SalesImportService salesImportService;
    private final SalesImportTemplateService salesImportTemplateService;

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('POS_ACCESS')")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(salesImportTemplateService.build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('POS_ACCESS')")
    public SalesImportSummary importFile(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "branchId", required = false) UUID branchId,
                                         @RequestParam(value = "bankId", required = false) UUID bankId) {
        return salesImportService.importFile(file, branchId, bankId);
    }
}
