package com.kesh.nlp.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FollowUpResolverTest {

    @Test
    void summarizeAboveUsesPreviousAssistantAndDoesNotSearch() {
        ClassificationResult current = ClassificationResult.single(
                CapabilityIntent.WEB_SEARCH, 0.4, java.util.Map.of(), "ml"
        );
        ClassificationResult follow = FollowUpResolver.maybeApply(
                "can you summarize above response",
                current,
                DeskContext.of(
                        null,
                        "traffic in Pune between Wadgaon Sheri to Yerwada now",
                        "Traffic is currently heavy between Wadgaon Sheri and Yerwada."
                )
        );
        assertThat(follow.intent()).isEqualTo(CapabilityIntent.WRITING_SUMMARIZE);
        assertThat(follow.flag("isFollowUp")).isTrue();
        assertThat(follow.entity("reference")).isEqualTo("PREVIOUS_ASSISTANT_RESPONSE");
        assertThat(follow.flag("reuseContext")).isTrue();
        assertThat(follow.entity("source").toString()).contains("currently");
    }

    @Test
    void checkAgainRequestsFreshWebSearch() {
        ConversationState state = new ConversationState();
        state.recordTurn(
                "Pune traffic",
                java.util.List.of("Wadgaon Sheri", "Yerwada"),
                "current",
                "WEB_SEARCH",
                "WEB_SEARCH",
                "web_search",
                "r1",
                "CURRENT_KNOWLEDGE",
                true,
                "traffic in Pune between Wadgaon Sheri to Yerwada now",
                java.util.List.of()
        );
        ClassificationResult follow = FollowUpResolver.maybeApply(
                "check again",
                ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.3, java.util.Map.of(), "ml"),
                DeskContext.of(
                        null,
                        "traffic in Pune between Wadgaon Sheri to Yerwada now",
                        "Traffic is currently heavy.",
                        state
                )
        );
        assertThat(follow.intent()).isEqualTo(CapabilityIntent.WEB_SEARCH);
        assertThat(follow.flag("freshnessOverride")).isTrue();
        assertThat(follow.entity("query").toString()).contains("Wadgaon");
    }
}
