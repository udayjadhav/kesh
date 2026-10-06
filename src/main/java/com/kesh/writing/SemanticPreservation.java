package com.kesh.writing;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cheap local meaning check. Embeddings are optional later; this is Option 0.
 */
public final class SemanticPreservation {

    private static final Pattern URL = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?%?\\b");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^}]+\\}\\}|\\$\\{[^}]+\\}|<[A-Z_]+>");

    private SemanticPreservation() {
    }

    public static double jaccard(String original, String revised) {
        Set<String> left = tokens(original);
        Set<String> right = tokens(revised);
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int overlap = 0;
        for (String token : left) {
            if (right.contains(token)) {
                overlap++;
            }
        }
        int union = left.size() + right.size() - overlap;
        return union == 0 ? 0 : overlap / (double) union;
    }

    public static Set<String> missingFacts(String original, String revised) {
        Set<String> missing = new LinkedHashSet<>();
        for (String fact : facts(original)) {
            if (revised == null || !containsIgnoreCase(revised, fact)) {
                missing.add(fact);
            }
        }
        return missing;
    }

    public static boolean preserved(String original, String revised, double threshold) {
        if (original == null || original.isBlank()) {
            return true;
        }
        if (revised == null || revised.isBlank()) {
            return false;
        }
        if (!missingFacts(original, revised).isEmpty()) {
            return false;
        }
        return jaccard(original, revised) >= threshold;
    }

    static Set<String> facts(String text) {
        Set<String> facts = new LinkedHashSet<>();
        if (text == null) {
            return facts;
        }
        addAll(facts, URL.matcher(text));
        addAll(facts, EMAIL.matcher(text));
        addAll(facts, NUMBER.matcher(text));
        addAll(facts, PLACEHOLDER.matcher(text));
        return facts;
    }

    private static void addAll(Set<String> facts, Matcher matcher) {
        while (matcher.find()) {
            String value = matcher.group();
            if (value.endsWith(".") || value.endsWith(",") || value.endsWith(")") || value.endsWith("]")) {
                value = value.substring(0, value.length() - 1);
            }
            if (!value.isBlank()) {
                facts.add(value);
            }
        }
    }

    private static Set<String> tokens(String text) {
        Set<String> set = new LinkedHashSet<>();
        if (text == null) {
            return set;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 2) {
                set.add(token);
            }
        }
        return set;
    }

    private static boolean containsIgnoreCase(String hay, String needle) {
        return hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
