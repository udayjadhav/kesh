package com.kesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kesh.nlp.capability.CapabilityDomain;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.nlp.capability.ClassifiedIntent;

class CapabilityRouterTest {

    @Test
    void multiIntentBecomesMultipleSteps() {
        ClassificationResult result = new ClassificationResult(
                List.of(
                        new ClassifiedIntent(CapabilityDomain.WEB, CapabilityIntent.WEB_SEARCH, 0.94, Map.of("query", "Java 25")),
                        new ClassifiedIntent(CapabilityDomain.TOOLS, CapabilityIntent.FILE_SEARCH, 0.87, Map.of()),
                        new ClassifiedIntent(CapabilityDomain.LLM, CapabilityIntent.LLM_COMPARE, 0.96, Map.of())
                ),
                "ml",
                false,
                null
        );
        RoutingPlan plan = new CapabilityRouter().plan("compare latest Java with our project", result);
        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps().getFirst().intent()).isEqualTo(CapabilityIntent.WEB_SEARCH);
    }

    @Test
    void clarificationHasNoSteps() {
        ClassificationResult result = ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.2, Map.of(), "ml")
                .asClarification("Do you want a local check or a web search?");
        RoutingPlan plan = new CapabilityRouter().plan("check Java", result);
        assertThat(plan.needsClarification()).isTrue();
        assertThat(plan.steps()).isEmpty();
    }
}
