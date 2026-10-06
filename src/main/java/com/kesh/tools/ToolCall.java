package com.kesh.tools;

import java.util.Map;

public record ToolCall(String id, String tool, Map<String, Object> arguments) {

    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public static ToolCall of(String tool, Map<String, Object> arguments) {
        return new ToolCall(null, tool, arguments);
    }
}
