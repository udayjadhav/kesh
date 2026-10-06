package com.kesh.llm;

import java.util.List;

import com.kesh.tools.ToolCall;

public record LlmMessage(
        String role,
        String content,
        List<ToolCall> toolCalls,
        String toolName
) {

    public LlmMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, List.of(), null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, List.of(), null);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content, List.of(), null);
    }

    public static LlmMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return new LlmMessage("assistant", content, toolCalls, null);
    }

    public static LlmMessage tool(String toolName, String content) {
        return new LlmMessage("tool", content, List.of(), toolName);
    }
}
