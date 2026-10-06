package com.kesh.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationContextTest {

    @Test
    void transcriptHidesJudgeNudges() {
        ConversationContext context = new ConversationContext("c1", "You are Kesh.");
        context.addUser("1+1");
        context.addAssistant("2");
        context.addModelNudge(
                "The previous answer missed the user request (empty). Use allowed tools or answer the actual ask."
        );
        assertThat(context.transcript())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly("user:1+1", "assistant:2");
        assertThat(context.messages().stream().map(message -> message.content()))
                .anyMatch(content -> content != null && content.startsWith(ConversationContext.INTERNAL_NUDGE_PREFIX));
    }
}
