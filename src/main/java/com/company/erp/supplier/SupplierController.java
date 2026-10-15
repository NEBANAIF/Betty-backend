package com.company.erp.supplier;

import com.company.erp.supplier.dto.SupplierBalanceResponse;
import com.company.erp.supplier.dto.SupplierRequest;
import com.company.erp.supplier.dto.SupplierResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE') or hasAuthority('PURCHASE_CREATE')")
    public List<SupplierResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return supplierService.list(includeInactive);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE') or hasAuthority('PURCHASE_CREATE')")
    public SupplierResponse get(@PathVariable UUID id) {
        return supplierService.get(id);
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE') or hasAuthority('FINANCE_VIEW')")
    public SupplierBalanceResponse balance(@PathVariable UUID id) {
        return supplierService.getBalance(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return supplierService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public SupplierResponse setActive(@PathVariable UUID id, @RequestParam boolean active) {
        return supplierService.setActive(id, active);
    }
}
