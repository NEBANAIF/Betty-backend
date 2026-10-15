package com.company.erp.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        @NotBlank String name,
        String phone,
        @Email String email,
        String address,
        String taxNumber
) {
}
