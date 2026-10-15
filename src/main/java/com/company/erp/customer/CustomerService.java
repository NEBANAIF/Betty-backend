package com.company.erp.customer;

import com.company.erp.common.exception.ResourceNotFoundException;
import com.company.erp.customer.dto.CustomerBalanceResponse;
import com.company.erp.customer.dto.CustomerRequest;
import com.company.erp.customer.dto.CustomerResponse;
import com.company.erp.loan.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final LoanRepository loanRepository;

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String search, boolean activeOnly) {
        return customerRepository.search(search, activeOnly).stream().map(CustomerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return CustomerResponse.from(findOrThrow(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        customer.setActive(true);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = findOrThrow(id);
        apply(customer, request);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse setActive(UUID id, boolean active) {
        Customer customer = findOrThrow(id);
        customer.setActive(active);
        return CustomerResponse.from(customer);
    }

    /** Real Loan-backed figures from Phase 7 onward - see CustomerBalanceResponse. */
    @Transactional(readOnly = true)
    public CustomerBalanceResponse getBalance(UUID id) {
        findOrThrow(id);
        var totalBorrowed = loanRepository.sumOriginalForCustomer(id);
        var outstanding = loanRepository.sumOutstandingForCustomer(id);
        return CustomerBalanceResponse.of(id, totalBorrowed, outstanding);
    }

    Customer findOrThrow(UUID id) {
        return customerRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
    }

    private void apply(Customer customer, CustomerRequest request) {
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setAddress(request.address());
    }
}
