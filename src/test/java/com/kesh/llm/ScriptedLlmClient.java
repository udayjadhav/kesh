package com.kesh.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ScriptedLlmClient implements LlmClient {

    private final List<Supplier<LlmResponse>> script = new ArrayList<>();
    private int index;

    public ScriptedLlmClient then(LlmResponse response) {
        script.add(() -> response);
        return this;
    }

    public ScriptedLlmClient thenFail(String message) {
        script.add(() -> LlmResponse.failure(message));
        return this;
    }

    @Override
    public String providerName() {
        return "scripted";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return next();
    }

    @Override
    public void chatStreaming(LlmRequest request, StreamConsumer consumer) {
        LlmResponse response = next();
        if (response.failed()) {
            consumer.onError(new IllegalStateException(response.error()));
            return;
        }
        if (!response.hasToolCalls() && response.content() != null && !response.content().isBlank()) {
            consumer.onToken(response.content());
        }
        consumer.onComplete(response);
    }

    private LlmResponse next() {
        if (index >= script.size()) {
            return LlmResponse.failure("No scripted LLM response left");
        }
        return script.get(index++).get();
    }
}
