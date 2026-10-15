package com.company.erp.settings;

import com.company.erp.common.exception.BusinessRuleViolationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The sign-in page is configured by an admin but shown to everyone, before they sign in. It is stored as JSON,
 * so only a small known set of keys is accepted: text has a length limit, colours must be plain hex, and layout
 * and pattern must be one of a fixed list. Anything else is rejected rather than stored. (The browser shows the
 * text as plain text, never as HTML.)
 */
public final class LoginSettingsValidator {

    static final Map<String, Integer> TEXT_LIMITS = Map.of(
            "title", 60,
            "subtitle", 160,
            "tagline", 40,
            "headline", 120,
            "description", 240,
            "footer", 80,
            "buttonLabel", 30,
            "helpText", 120);
    static final Set<String> COLOR_KEYS = Set.of("panelColor", "panelColor2", "buttonColor");
    static final Set<String> LAYOUTS = Set.of("left", "right", "center");
    static final Set<String> PATTERNS = Set.of("grid", "dots", "none");
    static final int MAX_BULLETS = 4;
    static final int MAX_BULLET_LENGTH = 80;
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LoginSettingsValidator() {
    }

    /** Returns the cleaned JSON to store, or null for "use the built-in sign-in page". */
    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Map<String, Object> input;
        try {
            input = MAPPER.readValue(raw, new TypeReference<>() { });
        } catch (Exception e) {
            throw new BusinessRuleViolationException("The login page settings are not valid.");
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if ("bullets".equals(key)) {
                if (value != null) {
                    clean.put(key, cleanBullets(value));
                }
                continue;
            }
            if (value == null || (value instanceof String s && s.isBlank())) {
                continue;
            }
            if (TEXT_LIMITS.containsKey(key)) {
                String text = cleanText(key, value, "headline".equals(key));
                if (!text.isEmpty()) {
                    clean.put(key, text);
                }
            } else if (COLOR_KEYS.contains(key)) {
                if (!(value instanceof String s) || !HEX.matcher(s).matches()) {
                    throw new BusinessRuleViolationException("\"" + key + "\" must be a colour like #7a7d55.");
                }
                clean.put(key, s.toLowerCase());
            } else if ("layout".equals(key)) {
                clean.put(key, oneOf(key, value, LAYOUTS));
            } else if ("pattern".equals(key)) {
                clean.put(key, oneOf(key, value, PATTERNS));
            } else {
                throw new BusinessRuleViolationException("Unknown login page option \"" + key + "\".");
            }
        }
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(clean);
        } catch (Exception e) {
            throw new BusinessRuleViolationException("The login page settings are not valid.");
        }
    }

    private static String cleanText(String key, Object value, boolean allowNewLines) {
        if (!(value instanceof String s)) {
            throw new BusinessRuleViolationException("\"" + key + "\" must be text.");
        }
        // Control characters (and, except in the headline, line breaks) are not wanted in a one-line label.
        String text = s.replaceAll(allowNewLines ? "[\\p{Cntrl}&&[^\\n]]" : "\\p{Cntrl}", " ").trim();
        if (text.length() > TEXT_LIMITS.get(key)) {
            throw new BusinessRuleViolationException(
                    "\"" + key + "\" can be at most " + TEXT_LIMITS.get(key) + " characters long.");
        }
        return text;
    }

    private static List<String> cleanBullets(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new BusinessRuleViolationException("\"bullets\" must be a list of short sentences.");
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String s)) {
                throw new BusinessRuleViolationException("\"bullets\" must be a list of short sentences.");
            }
            String text = s.replaceAll("\\p{Cntrl}", " ").trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > MAX_BULLET_LENGTH) {
                throw new BusinessRuleViolationException(
                        "Each bullet can be at most " + MAX_BULLET_LENGTH + " characters long.");
            }
            out.add(text);
        }
        if (out.size() > MAX_BULLETS) {
            throw new BusinessRuleViolationException("At most " + MAX_BULLETS + " bullets are allowed.");
        }
        return out;
    }

    private static String oneOf(String key, Object value, Set<String> allowed) {
        if (!(value instanceof String s) || !allowed.contains(s)) {
            throw new BusinessRuleViolationException("\"" + key + "\" is not an available choice.");
        }
        return s;
    }
}
