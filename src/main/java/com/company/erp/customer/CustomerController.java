package com.company.erp.customer;

import com.company.erp.customer.dto.CustomerBalanceResponse;
import com.company.erp.customer.dto.CustomerRequest;
import com.company.erp.customer.dto.CustomerResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW') or hasAuthority('POS_ACCESS')")
    public List<CustomerResponse> search(@RequestParam(required = false) String search,
                                          @RequestParam(defaultValue = "true") boolean activeOnly) {
        return customerService.search(search, activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW') or hasAuthority('POS_ACCESS')")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW') or hasAuthority('LOAN_VIEW') or hasAuthority('POS_ACCESS')")
    public CustomerBalanceResponse balance(@PathVariable UUID id) {
        return customerService.getBalance(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('POS_ACCESS') or hasAuthority('SALES_CREATE')")
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('POS_ACCESS') or hasAuthority('SALES_CREATE')")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('POS_ACCESS') or hasAuthority('SALES_CREATE')")
    public CustomerResponse setActive(@PathVariable UUID id, @RequestParam boolean active) {
        return customerService.setActive(id, active);
    }
}
