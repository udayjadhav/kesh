package com.kesh.llm;

public interface StreamConsumer {

    void onToken(String token);

    void onComplete(LlmResponse response);

    void onError(Throwable error);
}
