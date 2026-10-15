package com.company.erp.bank;

import com.company.erp.bank.dto.BankOptionResponse;
import com.company.erp.bank.dto.BankRequest;
import com.company.erp.bank.dto.BankResponse;
import com.company.erp.bank.dto.BankStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Banks. Seeing the full list (with account numbers) and changing it are separate switches
 * (BANK_VIEW, BANK_MANAGE). The short "options" list feeds the payment dropdowns and only needs
 * the person to be taking payments - it carries no account numbers.
 */
@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @GetMapping
    @PreAuthorize("hasAuthority('BANK_VIEW')")
    public List<BankResponse> list() {
        return bankService.list();
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('POS_ACCESS', 'SALES_CREATE', 'PAYMENT_VIEW', 'BANK_VIEW', 'FINANCE_VIEW')")
    public List<BankOptionResponse> options() {
        return bankService.options();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BANK_MANAGE')")
    public BankResponse create(@Valid @RequestBody BankRequest request) {
        return bankService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BANK_MANAGE')")
    public BankResponse update(@PathVariable UUID id, @Valid @RequestBody BankRequest request) {
        return bankService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('BANK_MANAGE')")
    public BankResponse setStatus(@PathVariable UUID id, @Valid @RequestBody BankStatusRequest request) {
        return bankService.setActive(id, request.active());
    }
}
