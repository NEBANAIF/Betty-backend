package com.company.erp.user.dto;

import com.company.erp.user.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String role,
        UUID roleId,
        String roleName,
        UUID branchId,
        String branchName,
        boolean active,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.getAccessRole() != null ? user.getAccessRole().getId() : null,
                user.getAccessRole() != null ? user.getAccessRole().getName() : user.getRole().name().replace('_', ' '),
                user.getBranch() != null ? user.getBranch().getId() : null,
                user.getBranch() != null ? user.getBranch().getName() : null,
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
