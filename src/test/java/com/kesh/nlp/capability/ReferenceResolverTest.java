package com.kesh.nlp.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReferenceResolverTest {

    @Test
    void newContentTokenIsIndependent() {
        ReferenceResolver.Decision decision = ReferenceResolver.resolve(
                "stripe",
                ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.4, java.util.Map.of(), "ml"),
                DeskContext.of(null, "recent restructuring news", "About 7,300 roles.")
        );
        assertThat(decision.isFollowUp()).isFalse();
        assertThat(decision.clarification()).isFalse();
        assertThat(decision.reusePreviousTool()).isFalse();
    }

    @Test
    void punctuationOnlyIsClarification() {
        ReferenceResolver.Decision decision = ReferenceResolver.resolve(
                "***",
                ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.2, java.util.Map.of(), "ml"),
                DeskContext.of(null, "recent restructuring news", "About 7,300 roles.")
        );
        assertThat(decision.clarification()).isTrue();
        assertThat(decision.isFollowUp()).isFalse();
        assertThat(decision.reusePreviousTool()).isFalse();
    }

    @Test
    void quantityAfterCurrentTopicIsFollowUp() {
        ReferenceResolver.Decision decision = ReferenceResolver.resolve(
                "how many employees were laid off?",
                ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.4, java.util.Map.of(), "ml"),
                DeskContext.of(null, "recent restructuring news", "About 7,300 roles.")
        );
        assertThat(decision.isFollowUp()).isTrue();
        assertThat(decision.reference()).isEqualTo("PREVIOUS_TOPIC");
        assertThat(decision.requiresFreshData()).isTrue();
        assertThat(decision.reusePreviousTool()).isFalse();
    }
}
