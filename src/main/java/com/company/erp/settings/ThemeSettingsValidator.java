package com.company.erp.settings;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The look of the app is stored as JSON and later written into CSS by every browser, so only a small,
 * known set of keys is accepted, colours must be plain hex and fonts must come from a fixed list.
 * Anything else is rejected rather than stored.
 */
public final class ThemeSettingsValidator {

    static final Set<String> COLOR_KEYS = Set.of(
            "pageBg", "cardBg", "textColor", "mutedTextColor", "sidebarBg", "sidebarText", "sidebarAccent",
            "buttonColor", "tableHeadBg", "tableHeadText", "tableRowA", "tableRowB");
    static final Set<String> FONT_KEYS = Set.of("bodyFont", "headingFont", "tableFont");
    static final Set<String> FONTS = Set.of(
            "Inter", "Poppins", "Roboto", "Open Sans", "Montserrat",
            "Nunito", "Source Sans 3", "Lato", "Raleway", "Work Sans",
            "DM Sans", "Manrope", "Plus Jakarta Sans", "Rubik", "Outfit",
            "Noto Sans", "IBM Plex Sans", "Fira Sans", "Quicksand", "Karla",
            "Mulish", "Josefin Sans", "Space Grotesk", "Figtree", "Ubuntu",
            "Barlow", "Arial", "Verdana", "Tahoma", "Trebuchet MS",
            "Lora", "Cormorant Garamond", "Playfair Display", "Merriweather", "Libre Baskerville",
            "Crimson Text", "EB Garamond", "PT Serif", "DM Serif Display", "Bitter",
            "Noto Serif", "Spectral", "Cinzel", "Georgia", "Times New Roman",
            "JetBrains Mono", "IBM Plex Mono", "Courier New");
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ThemeSettingsValidator() {
    }

    /** Returns the cleaned JSON to store, or null for "use the built-in look". */
    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Map<String, Object> input;
        try {
            input = MAPPER.readValue(raw, new TypeReference<>() { });
        } catch (Exception e) {
            throw new BusinessRuleViolationException("The theme is not valid.");
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (value == null || (value instanceof String s && s.isBlank())) {
                continue;
            }
            if (COLOR_KEYS.contains(key)) {
                if (!(value instanceof String s) || !HEX.matcher(s).matches()) {
                    throw new BusinessRuleViolationException("\"" + key + "\" must be a colour like #7a7d55.");
                }
                clean.put(key, s.toLowerCase());
            } else if (FONT_KEYS.contains(key)) {
                if (!(value instanceof String s) || !FONTS.contains(s)) {
                    throw new BusinessRuleViolationException("\"" + key + "\" is not an available font.");
                }
                clean.put(key, s);
            } else if ("fontScale".equals(key)) {
                if (!(value instanceof Number n) || n.doubleValue() < 0.85 || n.doubleValue() > 1.3) {
                    throw new BusinessRuleViolationException("Text size must be between 85% and 130%.");
                }
                clean.put(key, Math.round(n.doubleValue() * 100) / 100.0);
            } else {
                throw new BusinessRuleViolationException("Unknown theme option \"" + key + "\".");
            }
        }
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(clean);
        } catch (Exception e) {
            throw new BusinessRuleViolationException("The theme is not valid.");
        }
    }
}
