package com.company.erp.auth.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        UUID userId,
        String name,
        String email,
        String role,
        String roleName,
        UUID branchId,
        String branchName,
        java.util.Set<String> permissions
) {
}
