package com.kesh.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kesh.nlp.Intent;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.Risk;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.tools.ToolResult;

class GroundingPolicyTest {

    @Test
    void previousAssistantQuantityIsNotEvidence() {
        RequestAnalysis analysis = current("how many employees were laid off?");
        String enforced = GroundingPolicy.enforce(
                "Approximately 7,300 employees were laid off.",
                analysis,
                List.of()
        );
        assertThat(enforced).isEqualTo(GroundingPolicy.unverifiedCurrentAnswer());
        assertThat(enforced).doesNotContain("7,300");
    }

    @Test
    void quantityInToolResultMayStand() {
        RequestAnalysis analysis = current("how many employees were laid off?");
        ToolResult search = ToolResult.ok(
                "web_search",
                "The company said 7,300 roles are affected.",
                12,
                List.of(Map.of("title", "Filing", "url", "https://example.test/filing", "snippet", "7,300 roles"))
        );
        String enforced = GroundingPolicy.enforce(
                "Approximately 7,300 employees were laid off.",
                analysis,
                List.of(search)
        );
        assertThat(enforced).contains("7,300");
    }

    @Test
    void staticKnowledgeKeepsNumbers() {
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.CHAT,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                true,
                java.util.Set.of(),
                false,
                null,
                ClassificationResult.single(
                        CapabilityIntent.LLM_EXPLAIN,
                        0.9,
                        Map.of("knowledgeRequirement", "STATIC_KNOWLEDGE", "query", "what is CAP theorem"),
                        "test"
                ),
                null
        );
        assertThat(GroundingPolicy.enforce("CAP has 3 letters.", analysis, List.of()))
                .contains("3");
    }

    private static RequestAnalysis current(String query) {
        return new RequestAnalysis(
                Intent.WEB_SEARCH,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                true,
                java.util.Set.of("web_search"),
                false,
                null,
                ClassificationResult.single(
                        CapabilityIntent.WEB_SEARCH,
                        0.9,
                        Map.of("knowledgeRequirement", "CURRENT_KNOWLEDGE", "query", query),
                        "test"
                ),
                null
        );
    }
}
