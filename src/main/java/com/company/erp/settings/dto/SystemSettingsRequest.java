package com.company.erp.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SystemSettingsRequest(
        @NotNull Boolean lockCostPrice,
        @NotNull Boolean hideOutOfStockAtPos,
        /** Optional: when omitted the current name is left alone; when blank it resets to the default. */
        @Size(max = 60) String appName,
        /** Optional: omitted leaves the look alone; blank or "{}" resets it to the built-in look. */
        @Size(max = 4000) String themeJson,
        /** Optional: omitted leaves the sign-in page alone; blank or "{}" resets it to the built-in page. */
        @Size(max = 4000) String loginJson
) {
}
