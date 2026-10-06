package com.kesh.nlp.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeRequirementResolverTest {

    @Test
    void conceptualQuestionIsStaticKnowledge() {
        ClassificationResult classified = ClassificationResult.single(
                CapabilityIntent.LLM_EXPLAIN, 0.9, java.util.Map.of(), "ml"
        );
        assertThat(KnowledgeRequirementResolver.resolve(
                "tell me about AI system design", classified, DeskContext.of(null)
        )).isEqualTo(KnowledgeRequirement.STATIC_KNOWLEDGE);
    }

    @Test
    void latestPatternsAreCurrentKnowledge() {
        ClassificationResult classified = ClassificationResult.single(
                CapabilityIntent.WEB_SEARCH, 0.9, java.util.Map.of(), "ml"
        );
        assertThat(KnowledgeRequirementResolver.resolve(
                "what are the latest AI system design patterns?", classified, DeskContext.of(null)
        )).isEqualTo(KnowledgeRequirement.CURRENT_KNOWLEDGE);
    }

    @Test
    void marketQuoteShapeIsCurrentKnowledgeEvenIfClassifiedAsExplain() {
        ClassificationResult classified = ClassificationResult.single(
                CapabilityIntent.LLM_EXPLAIN, 0.9, java.util.Map.of(), "ml"
        );
        assertThat(KnowledgeRequirementResolver.resolve(
                "current share price", classified, DeskContext.of(null)
        )).isEqualTo(KnowledgeRequirement.CURRENT_KNOWLEDGE);
    }

    @Test
    void publicEventShapeIsCurrentKnowledge() {
        ClassificationResult classified = ClassificationResult.single(
                CapabilityIntent.LLM_EXPLAIN, 0.9, java.util.Map.of(), "ml"
        );
        assertThat(KnowledgeRequirementResolver.resolve(
                "tell me about recent restructuring", classified, DeskContext.of(null)
        )).isEqualTo(KnowledgeRequirement.CURRENT_KNOWLEDGE);
    }

    @Test
    void explicitSearchIsExternalResearch() {
        ClassificationResult classified = ClassificationResult.single(
                CapabilityIntent.WEB_SEARCH, 0.9, java.util.Map.of(), "ml"
        );
        assertThat(KnowledgeRequirementResolver.resolve(
                "search the web for AI system design", classified, DeskContext.of(null)
        )).isEqualTo(KnowledgeRequirement.EXTERNAL_RESEARCH);
    }
}
