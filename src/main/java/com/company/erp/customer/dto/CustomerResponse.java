package com.company.erp.customer.dto;

import com.company.erp.customer.Customer;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        boolean active
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(),
                customer.getEmail(), customer.getAddress(), customer.isActive());
    }
}
