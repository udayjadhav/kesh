package com.kesh.tools;

/**
 * Why a tool did not produce a usable result. Controls retry policy.
 */
public enum ToolFailureKind {
    ROUTING_FAILURE,
    TOOL_COMPATIBILITY_FAILURE,
    POLICY_FAILURE,
    PERMISSION_FAILURE,
    AUTH_FAILURE,
    TIMEOUT,
    TRANSIENT_FAILURE,
    INVALID_TOOL_CALL;

    public boolean mayRetrySameTool() {
        return this == TIMEOUT || this == TRANSIENT_FAILURE;
    }

    public static ToolFailureKind classify(String error) {
        if (error == null || error.isBlank()) {
            return TRANSIENT_FAILURE;
        }
        String lower = error.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return TIMEOUT;
        }
        if (lower.contains("502") || lower.contains("503") || lower.contains("429") || lower.contains("temporarily")) {
            return TRANSIENT_FAILURE;
        }
        if (lower.contains("denied") || lower.contains("not permitted") || lower.contains("policy")) {
            return POLICY_FAILURE;
        }
        if (lower.contains("auth") || lower.contains("unauthorized") || lower.contains("401") || lower.contains("403")) {
            return AUTH_FAILURE;
        }
        return TRANSIENT_FAILURE;
    }
}
