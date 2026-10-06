package com.kesh.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ChatTranscriptNormalizerTest {

    @Test
    void foldsExtraSystemMessagesAndKeepsUserAssistantTurns() {
        List<LlmMessage> normalized = ChatTranscriptNormalizer.forChatModel(List.of(
                LlmMessage.system("You are Kesh."),
                LlmMessage.user("hi"),
                LlmMessage.system("intent=CHAT"),
                LlmMessage.assistant("Hello there!"),
                LlmMessage.user("give me current time"),
                LlmMessage.system("intent=CHAT risk=READ")
        ));
        assertThat(normalized).hasSize(4);
        assertThat(normalized.get(0).role()).isEqualTo("system");
        assertThat(normalized.get(0).content()).contains("You are Kesh.").contains("intent=CHAT");
        assertThat(normalized.get(1).role()).isEqualTo("user");
        assertThat(normalized.get(2).role()).isEqualTo("assistant");
        assertThat(normalized.get(3).role()).isEqualTo("user");
        assertThat(normalized.get(3).content()).isEqualTo("give me current time");
    }

    @Test
    void mergesConsecutiveUserTurnsFromToolResults() {
        List<LlmMessage> normalized = ChatTranscriptNormalizer.forChatModel(List.of(
                LlmMessage.system("You are Kesh."),
                LlmMessage.user("what time is it"),
                LlmMessage.tool("current_time", "TOOL_RESULT SUCCESS: 15:00")
        ));
        assertThat(normalized).hasSize(2);
        assertThat(normalized.get(1).role()).isEqualTo("user");
        assertThat(normalized.get(1).content()).contains("what time is it").contains("TOOL_RESULT SUCCESS");
    }

    @Test
    void dropsWebToolResultsWhenWebIsNotAllowed() {
        List<LlmMessage> normalized = ChatTranscriptNormalizer.forChatModel(
                List.of(
                        LlmMessage.system("You are Kesh."),
                        LlmMessage.user("how is weather in Pune"),
                        LlmMessage.tool("web_search", "TOOL_RESULT (web_search) SUCCESS:\nPune 30C"),
                        LlmMessage.assistant("Partly cloudy in Pune."),
                        LlmMessage.user("1+1")
                ),
                Set.of("execute_java_snippet")
        );
        assertThat(normalized.stream().map(LlmMessage::content).toList().toString())
                .doesNotContain("TOOL_RESULT")
                .doesNotContain("Pune 30C")
                .contains("1+1");
    }

    @Test
    void dropsPreviousWebResultsOnANewWebQuestion() {
        List<LlmMessage> normalized = ChatTranscriptNormalizer.forChatModel(
                List.of(
                        LlmMessage.system("You are Kesh."),
                        LlmMessage.user("rain chances?"),
                        LlmMessage.tool("web_search", "TOOL_RESULT (web_search) SUCCESS:\nPune rain 20%"),
                        LlmMessage.assistant("20% chance of rain."),
                        LlmMessage.user("Harley 440T price in Pune")
                ),
                Set.of("web_search")
        );
        assertThat(normalized.stream().map(LlmMessage::content).toList().toString())
                .doesNotContain("Pune rain 20%")
                .contains("Harley 440T price in Pune");
    }

    @Test
    void independentTurnDropsPriorAssistantClaims() {
        List<LlmMessage> normalized = ChatTranscriptNormalizer.forChatModel(
                List.of(
                        LlmMessage.system("You are Kesh."),
                        LlmMessage.user("recent restructuring news"),
                        LlmMessage.assistant("About 7,300 roles."),
                        LlmMessage.user("stripe")
                ),
                Set.of("web_search"),
                true
        );
        assertThat(normalized.stream().map(LlmMessage::content).toList().toString())
                .contains("stripe")
                .doesNotContain("7,300")
                .doesNotContain("recent restructuring");
    }

    @Test
    void emptyAllowedToolsStillKeepsCurrentSearchNotes() {
        List<LlmMessage> normalized = ChatTranscriptNormalizer.forChatModel(
                List.of(
                        LlmMessage.system("You are Kesh."),
                        LlmMessage.user("oracle layoff"),
                        LlmMessage.tool("web_search", "TOOL_RESULT (web_search) SUCCESS:\nOracle is cutting 10,000 jobs")
                ),
                Set.of()
        );
        assertThat(normalized.stream().map(LlmMessage::content).toList().toString())
                .contains("Oracle is cutting 10,000 jobs")
                .contains("oracle layoff");
    }
}
