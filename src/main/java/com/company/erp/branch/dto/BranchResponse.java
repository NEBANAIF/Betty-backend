package com.company.erp.branch.dto;

import com.company.erp.branch.Branch;

import java.time.Instant;
import java.util.UUID;

public record BranchResponse(
        UUID id,
        String name,
        String code,
        String type,
        String address,
        String phone,
        String email,
        UUID managerId,
        String managerName,
        boolean active,
        Instant createdAt
) {
    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getCode(),
                branch.getType().name(),
                branch.getAddress(),
                branch.getPhone(),
                branch.getEmail(),
                branch.getManager() != null ? branch.getManager().getId() : null,
                branch.getManager() != null ? branch.getManager().getName() : null,
                branch.isActive(),
                branch.getCreatedAt()
        );
    }
}
