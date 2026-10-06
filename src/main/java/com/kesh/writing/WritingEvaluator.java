package com.kesh.writing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class WritingEvaluator {

    private WritingEvaluator() {
    }

    public static Map<String, Double> scores(
            String original,
            String output,
            CapabilityIntentKind kind,
            boolean preserveMeaning,
            boolean languageToolClean
    ) {
        Map<String, Double> scores = new LinkedHashMap<>();
        double grammar = languageToolClean ? 0.98 : 0.82;
        double semantic = original == null || original.isBlank()
                ? 1.0
                : SemanticPreservation.jaccard(original, output);
        Set<String> missing = SemanticPreservation.missingFacts(original, output);
        double facts = missing.isEmpty() ? 1.0 : Math.max(0.2, 1.0 - missing.size() * 0.2);
        double conciseness = conciseness(original, output, kind);
        double naturalness = naturalness(output);
        scores.put("grammar", round(grammar));
        scores.put("spelling", round(grammar));
        scores.put("punctuation", round(grammar));
        scores.put("semanticPreservation", round(semantic));
        scores.put("factualPreservation", round(facts));
        scores.put("clarity", round(Math.min(1.0, 0.7 + (1.0 - fillerRatio(output)) * 0.3)));
        scores.put("toneAdherence", round(preserveMeaning ? Math.min(semantic, 0.97) : 0.9));
        scores.put("conciseness", round(conciseness));
        scores.put("naturalness", round(naturalness));
        scores.put("instructionFollowing", round(preserveMeaning && !missing.isEmpty() ? 0.55 : 0.9));
        return scores;
    }

    private static double conciseness(String original, String output, CapabilityIntentKind kind) {
        if (original == null || original.isBlank() || output == null) {
            return 0.9;
        }
        if (kind == CapabilityIntentKind.EXPAND) {
            return output.length() >= original.length() ? 0.9 : 0.6;
        }
        if (kind == CapabilityIntentKind.SHORTEN) {
            return output.length() < original.length() ? 0.92 : 0.55;
        }
        if (output.length() > original.length() * 1.8) {
            return 0.55;
        }
        return 0.88;
    }

    private static double naturalness(String output) {
        if (output == null) {
            return 0;
        }
        double filler = fillerRatio(output);
        return Math.max(0.4, 1.0 - filler);
    }

    private static double fillerRatio(String output) {
        String lower = output.toLowerCase();
        int hits = 0;
        String[] fillers = {
                "i would like to kindly request",
                "at your earliest convenience",
                "the aforementioned",
                "please be advised",
                "reach out",
                "as per my last",
                "hope this message finds you"
        };
        for (String filler : fillers) {
            if (lower.contains(filler)) {
                hits++;
            }
        }
        return Math.min(1.0, hits * 0.2);
    }

    private static double round(double value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 100.0) / 100.0;
    }

    public enum CapabilityIntentKind {
        SHORTEN,
        EXPAND,
        OTHER
    }
}
