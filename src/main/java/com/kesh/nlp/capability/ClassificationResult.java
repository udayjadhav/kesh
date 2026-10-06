package com.kesh.nlp.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ML interpretation of a user message. Never includes permission or allow flags.
 */
public record ClassificationResult(
        List<ClassifiedIntent> intents,
        String source,
        boolean needsClarification,
        String clarificationQuestion
) {

    public ClassificationResult {
        intents = intents == null || intents.isEmpty()
                ? List.of(new ClassifiedIntent(CapabilityDomain.CHAT, CapabilityIntent.CHAT_GENERAL, 0.0, Map.of()))
                : List.copyOf(intents);
        source = source == null || source.isBlank() ? "ml" : source;
    }

    public static ClassificationResult single(
            CapabilityIntent intent,
            double confidence,
            Map<String, Object> entities,
            String source
    ) {
        return new ClassificationResult(
                List.of(new ClassifiedIntent(intent.domain(), intent, confidence, entities)),
                source,
                false,
                null
        );
    }

    public ClassifiedIntent primary() {
        return intents.getFirst();
    }

    public CapabilityDomain domain() {
        return primary().domain();
    }

    public CapabilityIntent intent() {
        return primary().intent();
    }

    public double confidence() {
        return primary().confidence();
    }

    public Map<String, Object> entities() {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (ClassifiedIntent item : intents) {
            merged.putAll(item.entities());
        }
        return merged;
    }

    public ClassificationResult withSource(String nextSource) {
        return new ClassificationResult(intents, nextSource, needsClarification, clarificationQuestion);
    }

    public ClassificationResult withEntities(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return this;
        }
        ClassifiedIntent primary = primary();
        Map<String, Object> merged = new LinkedHashMap<>(primary.entities());
        merged.putAll(extra);
        java.util.List<ClassifiedIntent> next = new java.util.ArrayList<>();
        next.add(new ClassifiedIntent(primary.domain(), primary.intent(), primary.confidence(), merged));
        for (int i = 1; i < intents.size(); i++) {
            next.add(intents.get(i));
        }
        return new ClassificationResult(next, source, needsClarification, clarificationQuestion);
    }

    public Object entity(String key) {
        return entities().get(key);
    }

    public boolean flag(String key) {
        Object value = entity(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    public ClassificationResult asClarification(String question) {
        return new ClassificationResult(intents, source, true, question);
    }

    public static ClassificationResult merge(ClassificationResult left, ClassificationResult right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        Map<CapabilityIntent, ClassifiedIntent> byIntent = new LinkedHashMap<>();
        for (ClassifiedIntent item : left.intents()) {
            byIntent.put(item.intent(), item);
        }
        for (ClassifiedIntent item : right.intents()) {
            ClassifiedIntent existing = byIntent.get(item.intent());
            if (existing == null || item.confidence() > existing.confidence()) {
                byIntent.put(item.intent(), item);
            }
        }
        List<ClassifiedIntent> merged = new ArrayList<>(byIntent.values());
        merged.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
        String source = left.confidence() >= right.confidence() ? left.source() : right.source();
        if (!left.source().equals(right.source())) {
            source = "merged:" + left.source() + "+" + right.source();
        }
        return new ClassificationResult(merged, source, false, null);
    }

    public static ClassificationResult prefer(ClassificationResult primary, ClassificationResult other) {
        if (primary == null) {
            return other;
        }
        if (other == null) {
            return primary;
        }
        List<ClassifiedIntent> intents = new ArrayList<>(primary.intents());
        for (ClassifiedIntent item : other.intents()) {
            if (conflicts(primary.intent(), item.intent())) {
                continue;
            }
            boolean present = intents.stream().anyMatch(existing -> existing.intent() == item.intent());
            if (!present) {
                intents.add(item);
            }
        }
        return new ClassificationResult(intents, primary.source(), false, null);
    }

    private static boolean conflicts(CapabilityIntent primary, CapabilityIntent extra) {
        if (primary == extra) {
            return false;
        }
        if ((primary.domain() == CapabilityDomain.LLM || primary.domain() == CapabilityDomain.CHAT)
                && (extra == CapabilityIntent.TERMINAL_EXECUTION || extra == CapabilityIntent.DESTRUCTIVE
                || extra == CapabilityIntent.APP_OPEN || extra == CapabilityIntent.RUNTIME_INSPECT
                || extra.domain() == CapabilityDomain.WEB)) {
            return true;
        }
        if ((primary.domain() == CapabilityDomain.LLM || primary.domain() == CapabilityDomain.CHAT)
                && extra.domain() == CapabilityDomain.WRITING) {
            return true;
        }
        if (primary == CapabilityIntent.LLM_CODE_HELP && extra.domain() == CapabilityDomain.SYSTEM) {
            return true;
        }
        return false;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", source);
        map.put("confidence", Math.round(confidence() * 100.0) / 100.0);
        map.put("needsClarification", needsClarification);
        if (clarificationQuestion != null) {
            map.put("clarificationQuestion", clarificationQuestion);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ClassifiedIntent item : intents) {
            rows.add(item.toMap());
        }
        map.put("intents", rows);
        map.put("domain", domain().name());
        map.put("intent", intent().name());
        map.put("entities", entities());
        return map;
    }
}
