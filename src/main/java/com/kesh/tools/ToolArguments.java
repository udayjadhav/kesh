package com.kesh.tools;

import java.util.Map;

public final class ToolArguments {

    private ToolArguments() {
    }

    public static String requireString(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value.toString().trim();
    }

    public static String optionalString(Map<String, Object> arguments, String key, String fallback) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString().trim();
    }

    public static int optionalInt(Map<String, Object> arguments, String key, int fallback) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
