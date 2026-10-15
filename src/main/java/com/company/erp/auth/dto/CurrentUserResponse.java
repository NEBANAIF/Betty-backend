package com.company.erp.auth.dto;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String name,
        String email,
        String role,
        String roleName,
        UUID branchId,
        String branchName,
        Set<String> permissions
) {
}
