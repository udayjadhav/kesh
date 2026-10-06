package com.kesh.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record RegisteredModel(
        String id,
        String model,
        String provider,
        String runtime,
        String baseUrl,
        Set<ModelRole> roles,
        int contextSize,
        int expectedLatencyMs,
        String memory,
        String parameterSize,
        boolean tools,
        boolean enabled,
        boolean discovered
) {

    public RegisteredModel {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        id = id == null || id.isBlank() ? model : id;
        model = model == null ? "" : model;
        provider = provider == null ? "" : provider;
        runtime = runtime == null ? provider : runtime;
        baseUrl = baseUrl == null ? "" : baseUrl;
        memory = memory == null ? "" : memory;
        parameterSize = parameterSize == null ? "" : parameterSize;
    }

    public boolean hasRole(ModelRole role) {
        return role != null && roles.contains(role);
    }

    public boolean matches(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String needle = name.strip();
        return needle.equalsIgnoreCase(id) || needle.equalsIgnoreCase(model);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("model", model);
        map.put("provider", provider);
        map.put("runtime", runtime);
        map.put("baseUrl", baseUrl);
        map.put("roles", roles.stream().map(Enum::name).toList());
        map.put("contextSize", contextSize);
        map.put("expectedLatencyMs", expectedLatencyMs);
        map.put("memory", memory);
        map.put("parameterSize", parameterSize);
        map.put("tools", tools);
        map.put("enabled", enabled);
        map.put("discovered", discovered);
        return map;
    }

    public static double billionParams(String parameterSize) {
        if (parameterSize == null || parameterSize.isBlank()) {
            return 0;
        }
        String raw = parameterSize.toLowerCase(Locale.ROOT).replace("parameters", "").replace(" ", "").trim();
        try {
            if (raw.endsWith("b")) {
                return Double.parseDouble(raw.substring(0, raw.length() - 1));
            }
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public List<String> roleNames() {
        return roles.stream().map(Enum::name).toList();
    }
}
