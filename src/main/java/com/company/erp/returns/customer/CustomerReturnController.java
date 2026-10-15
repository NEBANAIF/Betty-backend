package com.company.erp.returns.customer;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import com.company.erp.common.dto.PageResponse;
import com.company.erp.returns.customer.dto.CreateCustomerReturnRequest;
import com.company.erp.returns.customer.dto.CustomerReturnResponse;
import com.company.erp.returns.customer.dto.CustomerReturnSummaryResponse;
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

@RestController
@RequestMapping("/api/returns/customer")
@RequiredArgsConstructor
public class CustomerReturnController {

    private final CustomerReturnService customerReturnService;

    @GetMapping
    @PreAuthorize("hasAuthority('RETURN_VIEW')")
    public PageResponse<CustomerReturnSummaryResponse> search(
            @RequestParam(defaultValue = "ALL") BranchAccessService.BranchScope scope,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID saleId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return customerReturnService.search(scope, branchId, saleId, customerId, from, to, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RETURN_VIEW')")
    public CustomerReturnResponse get(@PathVariable UUID id) {
        return customerReturnService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_RETURN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerReturnResponse create(@Valid @RequestBody CreateCustomerReturnRequest request) {
        return customerReturnService.create(request);
    }
}
