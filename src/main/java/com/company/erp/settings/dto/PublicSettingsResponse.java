package com.company.erp.settings.dto;

import com.company.erp.settings.SystemSettings;

/** The only settings safe to expose before sign-in: the login screen needs the app name, the chosen look and the sign-in page wording. */
public record PublicSettingsResponse(String appName, String themeJson, String loginJson) {
    public static PublicSettingsResponse from(SystemSettings settings) {
        return new PublicSettingsResponse(settings.getAppName(), settings.getThemeJson(), settings.getLoginJson());
    }
}
