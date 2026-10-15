package com.company.erp.loan;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.loan.dto.LoanResponse;
import com.company.erp.loan.dto.LoanSummaryResponse;
import com.company.erp.loan.dto.RecordLoanPaymentRequest;
import com.company.erp.security.BranchAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Loans are a direct extension of credit sales: viewing them is gated by
 * LOAN_VIEW, and collecting a payment reuses SALES_CREATE, the same
 * permission needed to take a payment at POS.
 */
@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @GetMapping
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    public PageResponse<LoanSummaryResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            Pageable pageable) {
        return loanService.search(scope, branchId, customerId, status, dueFrom, dueTo, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    public LoanResponse get(@PathVariable UUID id) {
        return loanService.get(id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public LoanResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody RecordLoanPaymentRequest request) {
        return loanService.recordPayment(id, request);
    }
}
