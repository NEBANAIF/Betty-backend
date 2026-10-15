package com.company.erp.branch.dto;

import com.company.erp.branch.BranchType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBranchRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotNull BranchType type,
        String address,
        String phone,
        @Email String email
) {
}
