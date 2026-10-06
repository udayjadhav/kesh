package com.kesh.routing;

import java.util.Set;

import com.kesh.nlp.RequestAnalysis;
import com.kesh.tools.ToolCall;
import com.kesh.tools.ToolFailureKind;
import com.kesh.workspace.WorkspaceMode;

/**
 * A tool may run only when the routing plan / allow-list says so. The LLM is not the authorizer.
 */
public final class ToolCompatibilityValidator {

    public record Result(boolean compatible, ToolFailureKind kind, String reason) {
        public static Result ok() {
            return new Result(true, null, "");
        }

        public static Result reject(ToolFailureKind kind, String reason) {
            return new Result(false, kind, reason == null ? "" : reason);
        }
    }

    private ToolCompatibilityValidator() {
    }

    public static Result validate(ToolCall call, RequestAnalysis analysis, WorkspaceMode mode) {
        if (call == null || call.tool() == null || call.tool().isBlank()) {
            return Result.reject(ToolFailureKind.INVALID_TOOL_CALL, "empty tool");
        }
        if (mode != null && !mode.allowsTool(call.tool())) {
            return Result.reject(ToolFailureKind.POLICY_FAILURE, "desk does not allow tool");
        }
        if (analysis == null || !analysis.restrictTools()) {
            return Result.ok();
        }
        Set<String> allowed = analysis.allowedTools();
        if (allowed == null || !allowed.contains(call.tool())) {
            return Result.reject(ToolFailureKind.TOOL_COMPATIBILITY_FAILURE, "tool is not in the routing plan");
        }
        return Result.ok();
    }
}
