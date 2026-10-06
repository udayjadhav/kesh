package com.kesh.routing;

import java.util.LinkedHashMap;
import java.util.Map;

import com.kesh.nlp.capability.CapabilityDomain;
import com.kesh.nlp.capability.CapabilityIntent;

public record RoutingStep(
        CapabilityDomain domain,
        CapabilityIntent intent,
        String query,
        String target,
        double confidence
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domain", domain == null ? null : domain.name());
        map.put("intent", intent == null ? null : intent.name());
        if (query != null && !query.isBlank()) {
            map.put("query", query);
        }
        if (target != null && !target.isBlank()) {
            map.put("target", target);
        }
        map.put("confidence", Math.round(confidence * 100.0) / 100.0);
        return map;
    }
}
