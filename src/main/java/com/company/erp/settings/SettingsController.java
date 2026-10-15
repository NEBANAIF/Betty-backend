package com.company.erp.settings;

import com.company.erp.settings.dto.PublicSettingsResponse;
import com.company.erp.settings.dto.SystemSettingsRequest;
import com.company.erp.settings.dto.SystemSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SystemSettingsService systemSettingsService;

    /**
     * Readable by anyone signed in (no permission check beyond
     * authentication) - the New Product form needs to know whether cost is
     * locked regardless of whether the person creating a product also
     * manages Settings.
     */
    @GetMapping
    public SystemSettingsResponse get() {
        return systemSettingsService.get();
    }

    /** Unauthenticated: the login screen shows the app name before anyone has signed in. */
    @GetMapping("/public")
    public PublicSettingsResponse getPublic() {
        return systemSettingsService.getPublic();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SystemSettingsResponse update(@Valid @RequestBody SystemSettingsRequest request) {
        return systemSettingsService.update(request);
    }
}
