package com.kesh.writing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic writing plan. Built before the LLM runs.
 */
public record WritingPlan(
        String domain,
        String operation,
        WritingChannel medium,
        String audience,
        String tone,
        String rewriteIntensity,
        boolean preserveMeaning,
        boolean preserveFacts,
        boolean preserveStructure
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domain", domain);
        map.put("operation", operation);
        map.put("medium", medium == null ? "GENERIC" : medium.name());
        map.put("audience", audience);
        map.put("tone", tone);
        map.put("rewriteIntensity", rewriteIntensity);
        map.put("preserveMeaning", preserveMeaning);
        map.put("preserveFacts", preserveFacts);
        map.put("preserveStructure", preserveStructure);
        return map;
    }
}
