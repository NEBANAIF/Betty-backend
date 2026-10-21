package com.company.erp.debt;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.debt.dto.DebtPaymentRequest;
import com.company.erp.debt.dto.DebtRequest;
import com.company.erp.debt.dto.DebtResponse;
import com.company.erp.debt.dto.DebtSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The business's own credits and loans (money it owes). DEBT_VIEW to look, DEBT_MANAGE to add, edit and repay. */
@RestController
@RequestMapping("/api/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;

    @GetMapping
    @PreAuthorize("hasAuthority('DEBT_VIEW')")
    public PageResponse<DebtResponse> search(
            @RequestParam(required = false) DebtType type,
            @RequestParam(required = false) DebtStatus status,
            @RequestParam(defaultValue = "false") boolean outstandingOnly,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return debtService.search(type, status, outstandingOnly, overdueOnly, search, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DEBT_VIEW')")
    public DebtSummaryResponse summary() {
        return debtService.summary();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DEBT_VIEW')")
    public DebtResponse get(@PathVariable UUID id) {
        return debtService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DEBT_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DebtResponse create(@Valid @RequestBody DebtRequest request) {
        return debtService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DEBT_MANAGE')")
    public DebtResponse update(@PathVariable UUID id, @Valid @RequestBody DebtRequest request) {
        return debtService.update(id, request);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('DEBT_MANAGE')")
    public DebtResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody DebtPaymentRequest request) {
        return debtService.recordPayment(id, request);
    }
}
