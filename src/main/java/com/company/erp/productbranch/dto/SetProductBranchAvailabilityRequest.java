package com.company.erp.productbranch.dto;

import jakarta.validation.constraints.NotNull;

/** true = tick the checkbox (product belongs at this branch), false = untick (it doesn't). */
public record SetProductBranchAvailabilityRequest(@NotNull Boolean allowed) {
}
