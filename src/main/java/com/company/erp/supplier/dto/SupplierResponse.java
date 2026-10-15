package com.company.erp.supplier.dto;

import com.company.erp.supplier.Supplier;

import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        String taxNumber,
        boolean active
) {
    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(), supplier.getName(), supplier.getPhone(), supplier.getEmail(),
                supplier.getAddress(), supplier.getTaxNumber(), supplier.isActive());
    }
}
