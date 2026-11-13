package com.company.erp.common.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates the English messages the services throw into Amharic when the caller asks for it
 * (request header {@code Accept-Language: am}, which the web app sends after the user picks አማርኛ).
 *
 * <p>The messages stay English in the code. The translations live in
 * {@code src/main/resources/i18n/messages_am.tsv}: one message per line, English then a TAB then
 * Amharic, with {@code {1}}, {@code {2}}... standing for the variable parts (names, numbers).
 * A message with no translation is returned unchanged, so nothing can break.
 */
@Component
public class MessageTranslator {

    private static final Logger log = LoggerFactory.getLogger(MessageTranslator.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");
    private static final int MAX_DEPTH = 2;

    private record Entry(Pattern regex, String template, int weight, java.util.Set<Integer> translatedGroups) { }

    private final Map<String, String> exact = new HashMap<>();
    private final List<Entry> patterns = new ArrayList<>();

    public MessageTranslator() {
        load();
    }

    /** True when the Accept-Language header's first choice is Amharic. */
    public static boolean wantsAmharic(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return false;
        }
        String first = acceptLanguage.split(",")[0].split(";")[0].trim().toLowerCase(Locale.ROOT);
        return first.equals("am") || first.startsWith("am-");
    }

    /** The message in the caller's language (unchanged when English is wanted or no translation exists). */
    public String translate(String message, String acceptLanguage) {
        if (message == null || message.isBlank() || !wantsAmharic(acceptLanguage)) {
            return message;
        }
        String translated = lookup(message.trim(), 0);
        return translated != null ? translated : message;
    }

    private String lookup(String text, int depth) {
        String hit = exact.get(text);
        if (hit != null) {
            return hit;
        }
        if (depth >= MAX_DEPTH) {
            return null;
        }
        for (Entry e : patterns) {
            Matcher m = e.regex().matcher(text);
            if (m.matches()) {
                return fill(e, m, depth);
            }
        }
        return null;
    }

    private String fill(Entry e, Matcher m, int depth) {
        Matcher p = PLACEHOLDER.matcher(e.template());
        StringBuilder out = new StringBuilder();
        while (p.find()) {
            int group = Integer.parseInt(p.group(1));
            String captured = group <= m.groupCount() ? m.group(group) : "";
            // Status-like words (RECEIVED, approved) are translated; capitalised text is a name and is kept as is -
            // except the groups a template marks as a label or a nested message ("{1} not found", "Row {1}: {2}").
            boolean translatable = captured != null
                    && (captured.equals(captured.toLowerCase(Locale.ROOT))
                    || captured.equals(captured.toUpperCase(Locale.ROOT))
                    || e.translatedGroups().contains(group));
            String inner = translatable ? lookup(captured, depth + 1) : null;
            p.appendReplacement(out, Matcher.quoteReplacement(inner != null ? inner : (captured == null ? "" : captured)));
        }
        p.appendTail(out);
        return out.toString();
    }

    private void load() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("i18n/messages_am.tsv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                String en = line.substring(0, tab).trim();
                String am = line.substring(tab + 1).trim();
                if (PLACEHOLDER.matcher(en).find()) {
                    patterns.add(compile(en, am));
                } else {
                    exact.put(en, am);
                }
            }
            // Most specific (longest fixed text) first, so a general pattern cannot shadow a precise one.
            patterns.sort(Comparator.comparingInt(Entry::weight).reversed());
        } catch (IOException | RuntimeException e) {
            log.warn("Amharic message file could not be loaded - messages will stay in English: {}", e.getMessage());
        }
    }

    private static Entry compile(String english, String amharic) {
        Matcher m = PLACEHOLDER.matcher(english);
        StringBuilder regex = new StringBuilder("^");
        int last = 0;
        int literal = 0;
        while (m.find()) {
            String part = english.substring(last, m.start());
            regex.append(Pattern.quote(part)).append("(.+?)");
            literal += part.length();
            last = m.end();
        }
        String tail = english.substring(last);
        regex.append(Pattern.quote(tail)).append('$');
        literal += tail.length();
        java.util.Set<Integer> groups = new java.util.HashSet<>();
        if (english.startsWith("{1} not found")) {
            groups.add(1);
        }
        if (english.startsWith("Row {1}: ")) {
            groups.add(2);
        }
        return new Entry(Pattern.compile(regex.toString(), Pattern.DOTALL), amharic, literal, groups);
    }
}
