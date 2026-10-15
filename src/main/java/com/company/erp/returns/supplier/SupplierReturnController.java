package com.company.erp.returns.supplier;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.returns.supplier.dto.CreateSupplierReturnRequest;
import com.company.erp.returns.supplier.dto.SupplierReturnResponse;
import com.company.erp.returns.supplier.dto.SupplierReturnSummaryResponse;
import com.company.erp.security.BranchAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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

import java.util.UUID;

/**
 * No dedicated SUPPLIER_RETURN permission exists in the fixed set (spec
 * section 30) - gated with PURCHASE_RECEIVE, the same authority that
 * receives goods in the first place (held by STORE_MANAGER,
 * WAREHOUSE_MANAGER, WAREHOUSE_STAFF - never STORE_STAFF, who never
 * handles purchasing either).
 */
@RestController
@RequestMapping("/api/returns/supplier")
@RequiredArgsConstructor
public class SupplierReturnController {

    private final SupplierReturnService supplierReturnService;

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_RECEIVE') or hasAuthority('SUPPLIER_MANAGE')")
    public PageResponse<SupplierReturnSummaryResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID purchaseOrderId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return supplierReturnService.search(scope, branchId, supplierId, purchaseOrderId, from, to, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_RECEIVE') or hasAuthority('SUPPLIER_MANAGE')")
    public SupplierReturnResponse get(@PathVariable UUID id) {
        return supplierReturnService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_RECEIVE')")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierReturnResponse create(@Valid @RequestBody CreateSupplierReturnRequest request) {
        return supplierReturnService.create(request);
    }
}
