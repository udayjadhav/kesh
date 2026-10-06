package com.kesh.llm;

import java.util.List;

import com.kesh.tools.ToolCall;

public record LlmResponse(String content, List<ToolCall> toolCalls, boolean failed, String error) {

    public LlmResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        content = content == null ? "" : content;
    }

    public static LlmResponse text(String content) {
        return new LlmResponse(content, List.of(), false, null);
    }

    public static LlmResponse tools(String content, List<ToolCall> toolCalls) {
        return new LlmResponse(content, toolCalls, false, null);
    }

    public static LlmResponse failure(String error) {
        return new LlmResponse("", List.of(), true, error);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
