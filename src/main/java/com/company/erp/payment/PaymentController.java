package com.company.erp.payment;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.payment.dto.PaymentResponse;
import com.company.erp.security.BranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    /** The Payments page table: every payment the caller may see, newest first, with optional filters. */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public PageResponse<PaymentResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) UUID bankId,
            @RequestParam(required = false) PaymentReferenceType referenceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return paymentQueryService.search(scope, branchId, method, bankId, referenceType, from, to, search, pageable);
    }

    /** Every payment recorded against one specific sale, loan repayment, etc. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_VIEW', 'LOAN_VIEW', 'FINANCE_VIEW', 'PAYMENT_VIEW')")
    public List<PaymentResponse> byReference(@RequestParam PaymentReferenceType referenceType,
                                              @RequestParam UUID referenceId) {
        return paymentQueryService.byReference(referenceType, referenceId);
    }
}
