package com.company.erp.expense;

import com.company.erp.common.dto.PageResponse;
import com.company.erp.expense.dto.ExpenseRequest;
import com.company.erp.expense.dto.ExpenseResponse;
import com.company.erp.security.BranchAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Viewing the list is gated to EXPENSE_VIEW (managers only, by default):
 * EXPENSE_CREATE is held by every branch role (any staff member can log a
 * routine expense receipt) but EXPENSE_VIEW only by managers - staff
 * records expenses without seeing the branch's full financial picture.
 * See RolePermissionRegistry.
 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_VIEW')")
    public PageResponse<ExpenseResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return expenseService.search(scope, branchId, category, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_VIEW')")
    public ExpenseResponse get(@PathVariable UUID id) {
        return expenseService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request) {
        return expenseService.create(request);
    }
}
