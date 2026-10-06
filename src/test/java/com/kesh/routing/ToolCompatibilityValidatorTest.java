package com.kesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.kesh.nlp.Intent;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.Risk;
import com.kesh.nlp.capability.CapabilityDomain;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.tools.ToolCall;
import com.kesh.tools.ToolFailureKind;
import com.kesh.workspace.WorkspaceMode;

class ToolCompatibilityValidatorTest {

    @Test
    void javaIsIncompatibleWithMarketRouting() {
        RequestAnalysis plan = new RequestAnalysis(
                Intent.WEB_SEARCH,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                true,
                Set.of("web_search"),
                false,
                null,
                ClassificationResult.single(
                        CapabilityIntent.WEB_SEARCH,
                        0.9,
                        Map.of("requestClass", "MARKET_DATA"),
                        "test"
                ),
                new RoutingPlan(List.of(
                        new RoutingStep(CapabilityDomain.WEB, CapabilityIntent.WEB_SEARCH, "current share price", null, 0.9)
                ), false, null)
        );
        ToolCompatibilityValidator.Result result = ToolCompatibilityValidator.validate(
                ToolCall.of("execute_java_snippet", Map.of("code", "System.out.println(1);")),
                plan,
                WorkspaceMode.PROFESSIONAL
        );
        assertThat(result.compatible()).isFalse();
        assertThat(result.kind()).isEqualTo(ToolFailureKind.TOOL_COMPATIBILITY_FAILURE);
        assertThat(ToolFailureKind.TOOL_COMPATIBILITY_FAILURE.mayRetrySameTool()).isFalse();
    }

    @Test
    void allowedWebToolIsCompatible() {
        RequestAnalysis plan = new RequestAnalysis(
                Intent.WEB_SEARCH, 0.9, Risk.READ, 0.9, List.of(), true, Set.of("web_search"), false, null
        );
        assertThat(ToolCompatibilityValidator.validate(
                ToolCall.of("web_search", Map.of("query", "latest news")),
                plan,
                WorkspaceMode.PROFESSIONAL
        ).compatible()).isTrue();
    }
}
