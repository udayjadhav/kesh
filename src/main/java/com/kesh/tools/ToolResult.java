package com.kesh.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolResult(
        String tool,
        boolean success,
        String output,
        String error,
        long durationMs,
        List<Map<String, Object>> sources,
        String executionId,
        String status
) {

    public ToolResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
        executionId = executionId == null || executionId.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : executionId;
        status = status == null || status.isBlank()
                ? (success ? "SUCCESS" : "FAILURE")
                : status;
    }

    public ToolResult(
            String tool,
            boolean success,
            String output,
            String error,
            long durationMs,
            List<Map<String, Object>> sources
    ) {
        this(tool, success, output, error, durationMs, sources, null, null);
    }

    public static ToolResult ok(String tool, String output, long durationMs) {
        return new ToolResult(tool, true, output, null, durationMs, List.of());
    }

    public static ToolResult ok(String tool, String output, long durationMs, List<Map<String, Object>> sources) {
        return new ToolResult(tool, true, output, null, durationMs, sources);
    }

    public static ToolResult fail(String tool, String error, long durationMs) {
        return new ToolResult(tool, false, null, error, durationMs, List.of());
    }

    public String renderForModel() {
        if (success) {
            return "TOOL_RESULT (" + tool + ") SUCCESS:\n" + nullToEmpty(output);
        }
        return "TOOL_RESULT (" + tool + ") FAILED:\n" + nullToEmpty(error);
    }

    public List<Map<String, Object>> sourceMaps() {
        return sources;
    }

    public Map<String, Object> toEventMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tool", tool);
        map.put("success", success);
        map.put("output", output == null ? "" : output);
        map.put("error", error == null ? "" : error);
        map.put("sources", sources);
        map.put("executionId", executionId);
        map.put("status", status);
        return map;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
