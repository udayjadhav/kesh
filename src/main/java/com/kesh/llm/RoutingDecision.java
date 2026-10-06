package com.kesh.llm;

import java.util.LinkedHashMap;
import java.util.Map;

public record RoutingDecision(
        String model,
        String provider,
        String baseUrl,
        ModelRole role,
        RequestComplexity complexity,
        boolean fallback,
        String fallbackFrom,
        String reason
) {

    public RoutingDecision {
        model = model == null ? "" : model;
        provider = provider == null ? "" : provider;
        baseUrl = baseUrl == null ? "" : baseUrl;
        reason = reason == null ? "" : reason;
        fallbackFrom = fallbackFrom == null ? "" : fallbackFrom;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", model);
        map.put("provider", provider);
        map.put("baseUrl", baseUrl);
        map.put("role", role == null ? "" : role.name());
        map.put("complexity", complexity == null ? "" : complexity.name());
        map.put("fallback", fallback);
        map.put("fallbackFrom", fallbackFrom);
        map.put("reason", reason);
        return map;
    }
}
