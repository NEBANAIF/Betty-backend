package com.company.erp.finance;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.finance.dto.BranchPnLResponse;
import com.company.erp.finance.dto.FinancialTransactionResponse;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceReportService financeReportService;

    @GetMapping("/pnl")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public BranchPnLResponse getPnL(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return financeReportService.getPnL(scope, branchId, from, to);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public PageResponse<FinancialTransactionResponse> searchTransactions(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) FinancialTransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return financeReportService.searchTransactions(scope, branchId, type, from, to, pageable);
    }
}
