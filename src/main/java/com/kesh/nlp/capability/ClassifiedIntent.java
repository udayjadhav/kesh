package com.kesh.nlp.capability;

import java.util.LinkedHashMap;
import java.util.Map;

public record ClassifiedIntent(
        CapabilityDomain domain,
        CapabilityIntent intent,
        double confidence,
        Map<String, Object> entities
) {

    public ClassifiedIntent {
        domain = domain == null ? (intent == null ? CapabilityDomain.CHAT : intent.domain()) : domain;
        intent = intent == null ? CapabilityIntent.CHAT_GENERAL : intent;
        entities = entities == null ? Map.of() : Map.copyOf(entities);
        confidence = Double.isNaN(confidence) ? 0.0 : Math.max(0.0, Math.min(1.0, confidence));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domain", domain.name());
        map.put("intent", intent.name());
        map.put("confidence", Math.round(confidence * 100.0) / 100.0);
        map.put("entities", entities);
        return map;
    }
}
