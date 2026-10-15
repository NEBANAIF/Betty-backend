package com.company.erp.settings.dto;

import com.company.erp.settings.SystemSettings;

public record SystemSettingsResponse(
        boolean lockCostPrice,
        boolean hideOutOfStockAtPos,
        String appName,
        String themeJson,
        String loginJson
) {
    public static SystemSettingsResponse from(SystemSettings settings) {
        return new SystemSettingsResponse(settings.isLockCostPrice(), settings.isHideOutOfStockAtPos(),
                settings.getAppName(), settings.getThemeJson(), settings.getLoginJson());
    }
}
