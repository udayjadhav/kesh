package com.kesh.llm;

public interface LlmClient {

    String providerName();

    LlmResponse chat(LlmRequest request);

    void chatStreaming(LlmRequest request, StreamConsumer consumer);
}
