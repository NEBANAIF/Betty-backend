package com.company.erp.branch.dto;

import java.util.UUID;

/** userId may be null to unassign the current manager. */
public record AssignManagerRequest(
        UUID userId
) {
}
