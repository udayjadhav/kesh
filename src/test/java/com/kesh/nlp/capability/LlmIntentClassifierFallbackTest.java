package com.kesh.nlp.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmIntentClassifierFallbackTest {

    @Test
    void extractJsonIgnoresMarkdownFences() {
        String raw = "```json\n{\"intent\":\"WEB_SEARCH\",\"confidence\":0.9}\n```";
        assertThat(LlmIntentClassifierFallback.extractJson(raw)).contains("WEB_SEARCH");
    }
}
