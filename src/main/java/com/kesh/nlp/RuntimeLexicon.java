package com.kesh.nlp;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared lexicon for "what is installed on this Mac" questions.
 * Used by the classifier, entity extractor, and decision engine.
 */
public final class RuntimeLexicon {

    private static final Map<String, String> CANONICAL;
    private static final Pattern SUBJECT;
    private static final Pattern VERSION_ASK = Pattern.compile(
            "(?i)\\b(version|installed|sw_vers|what(?:['’]?s| is).{0,24}running)\\b"
                    + "|\\bwhich\\b.{0,24}\\bversion\\b"
                    + "|\\bjava\\s+-version\\b"
                    + "|\\bnode\\s+-v\\b"
    );
    private static final Pattern LOCAL_INSTALL = Pattern.compile(
            "(?i)\\b(installed|on this mac|sw_vers)\\b"
                    + "|\\bjava\\s+-version\\b"
                    + "|\\bnode\\s+-v\\b"
                    + "|\\bwhich\\b.{0,32}\\b(cursor|mac|macos)\\b"
    );
    private static final Pattern PUBLIC_CURRENT = Pattern.compile(
            "(?i)\\b(latest|current release|recent release|released)\\b"
    );

    static {
        Map<String, String> map = new LinkedHashMap<>();
        put(map, "java", "java", "jdk", "jre", "openjdk");
        put(map, "javac", "javac");
        put(map, "node", "node", "nodejs", "node.js");
        put(map, "npm", "npm");
        put(map, "python", "python", "python3", "py");
        put(map, "mvn", "mvn", "maven");
        put(map, "git", "git");
        put(map, "docker", "docker");
        put(map, "go", "go", "golang");
        put(map, "ruby", "ruby");
        put(map, "php", "php");
        put(map, "rustc", "rustc", "rust");
        put(map, "swift", "swift");
        put(map, "cursor", "cursor");
        put(map, "macos", "macos", "mac", "osx", "darwin", "os");
        CANONICAL = Map.copyOf(map);
        SUBJECT = Pattern.compile("(?i)\\b(" + String.join("|", CANONICAL.keySet().stream()
                .map(Pattern::quote)
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList()) + "|mac\\s+os|node\\.js)\\b");
    }

    private RuntimeLexicon() {
    }

    public static boolean isVersionAsk(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT).strip();
        if (lower.startsWith("why") || lower.startsWith("how come")) {
            return false;
        }
        if (PUBLIC_CURRENT.matcher(lower).find() && !LOCAL_INSTALL.matcher(lower).find()) {
            return false;
        }
        return VERSION_ASK.matcher(lower).find() && findCanonical(text) != null;
    }

    public static String findCanonical(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = SUBJECT.matcher(text.toLowerCase(Locale.ROOT));
        String first = null;
        while (matcher.find()) {
            String raw = matcher.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if ("mac os".equals(raw)) {
                raw = "macos";
            }
            if ("node.js".equals(raw)) {
                raw = "node";
            }
            String mapped = CANONICAL.getOrDefault(raw, raw);
            if ("java".equals(mapped) || "node".equals(mapped) || "cursor".equals(mapped) || "python".equals(mapped)) {
                return mapped;
            }
            if (first == null) {
                first = mapped;
            }
        }
        return first;
    }

    public static String canonicalOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).trim();
        if (key.startsWith("mac os")) {
            key = "macos";
        }
        return CANONICAL.get(key);
    }

    private static void put(Map<String, String> map, String canonical, String... aliases) {
        for (String alias : aliases) {
            map.put(alias, canonical);
        }
    }
}
