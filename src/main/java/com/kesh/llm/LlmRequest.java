package com.kesh.llm;

import java.util.List;

public record LlmRequest(String model, List<LlmMessage> messages, boolean stream) {

    public LlmRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
