package com.kesh.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.nlp.RequestAnalysis;

class GemmaAnswerJudgeTest {

    @Test
    void rejectsCannedClarificationOnACompleteQuestion() {
        GemmaAnswerJudge judge = new GemmaAnswerJudge(null, new KeshProperties(), new ObjectMapper());
        AnswerJudge.JudgeVerdict verdict = judge.evaluate(
                "how is weather in Pune",
                "I am not sure what you want. Do you want a local machine check, a file/project look-up, a web search, or just an explanation?",
                null,
                List.of()
        );
        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.retryIntents()).isNotEmpty();
    }

    @Test
    void acceptsADirectAnswer() {
        GemmaAnswerJudge judge = new GemmaAnswerJudge(null, new KeshProperties(), new ObjectMapper());
        AnswerJudge.JudgeVerdict verdict = judge.evaluate(
                "Hi",
                "Hello there! How can I help you today?",
                null,
                List.of()
        );
        assertThat(verdict.ok()).isTrue();
    }

    @Test
    void doesNotInjectWebSearchWhenWebIsNotAllowed() {
        RequestAnalysis analysis = new RequestAnalysis(
                com.kesh.nlp.Intent.CHAT,
                0.9,
                com.kesh.nlp.Risk.READ,
                0.9,
                List.of(),
                true,
                java.util.Set.of(),
                false,
                null
        );
        GemmaAnswerJudge judge = new GemmaAnswerJudge(null, new KeshProperties(), new ObjectMapper());
        AnswerJudge.JudgeVerdict verdict = judge.evaluate(
                "how is two plus two computed",
                "I am not sure what you want. Do you want a local machine check, a file/project look-up, a web search, or just an explanation?",
                analysis,
                List.of()
        );
        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.retryIntents()).doesNotContain(com.kesh.nlp.capability.CapabilityIntent.WEB_SEARCH);
        assertThat(verdict.retryIntents()).contains(com.kesh.nlp.capability.CapabilityIntent.LLM_EXPLAIN);
    }

    @Test
    void rejectsToolDumpAndApologyWithoutAddingWeb() {
        GemmaAnswerJudge judge = new GemmaAnswerJudge(null, new KeshProperties(), new ObjectMapper());
        AnswerJudge.JudgeVerdict dump = judge.evaluate(
                "clojure hello world",
                "TOOL_RESULT (web_search) SUCCESS:\nJSON: {\"results\":[]}\nDo not invent extra URLs.",
                null,
                List.of()
        );
        assertThat(dump.ok()).isFalse();
        assertThat(dump.retryIntents()).containsExactly(com.kesh.nlp.capability.CapabilityIntent.LLM_EXPLAIN);

        AnswerJudge.JudgeVerdict apology = judge.evaluate(
                "best car to buy",
                "You are absolutely right. My apologies for the repeated misinterpretations.",
                null,
                List.of()
        );
        assertThat(apology.ok()).isFalse();
        assertThat(apology.retryIntents()).doesNotContain(com.kesh.nlp.capability.CapabilityIntent.WEB_SEARCH);
    }
}
