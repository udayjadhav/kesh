package com.kesh.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.CapabilityTrainingData;
import com.kesh.nlp.capability.DeskContext;
import com.kesh.nlp.capability.HybridCapabilityClassifier;
import com.kesh.nlp.capability.KnowledgeRequirement;
import com.kesh.nlp.capability.OpenNlpCapabilityClassifier;
import com.kesh.routing.CapabilityRouter;
import com.kesh.tools.FakeTool;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;

class RoutingArchitectureTest {

    private RequestAnalyzer analyzer;
    private List<Tool> catalog;

    @BeforeEach
    void setUp() {
        NlpTrainingData data = new NlpTrainingData(
                new ClassPathResource("nlp/intent-train.tsv"),
                new ClassPathResource("nlp/risk-train.tsv")
        );
        KeshProperties properties = new KeshProperties();
        properties.getNlp().getClassifier().setFallbackToLlm(false);
        properties.getNlp().getClassifier().setClarificationEnabled(false);
        analyzer = new RequestAnalyzer(
                new HybridCapabilityClassifier(properties, new OpenNlpCapabilityClassifier(
                        new CapabilityTrainingData(
                                new ObjectMapper(),
                                new ClassPathResource("nlp/kesh-intents.jsonl"),
                                new ClassPathResource("nlp/evaluation.jsonl")
                        )
                )),
                new RiskClassifier(data),
                new EntityExtractor(),
                new NlpDecisionEngine(properties),
                new CapabilityRouter(),
                properties
        );
        catalog = List.of(
                new FakeTool("web_search", PermissionLevel.READ),
                new FakeTool("web_fetch", PermissionLevel.READ),
                new FakeTool("web_crawl", PermissionLevel.READ),
                new FakeTool("execute_java_snippet", PermissionLevel.DANGEROUS),
                new FakeTool("inspect_runtime", PermissionLevel.READ),
                new FakeTool("get_my_jira_issues", PermissionLevel.READ),
                new FakeTool("search_confluence", PermissionLevel.READ)
        );
    }

    @Test
    void staticKnowledgeDoesNotCallTools() {
        for (String ask : List.of(
                "meaning of Uday",
                "what is RAG?",
                "explain CAP theorem",
                "inner join vs outer join",
                "compare Java and Clojure"
        )) {
            RequestAnalysis analysis = analyzer.analyze(ask, catalog);
            assertThat(analysis.skipModel()).isFalse();
            assertThat(analysis.allowedTools()).doesNotContain("execute_java_snippet", "web_search");
            assertThat(knowledge(analysis)).isEqualTo(KnowledgeRequirement.STATIC_KNOWLEDGE.name());
        }
    }

    @Test
    void marketQuotesRouteToWebNeverJava() {
        for (String ask : List.of(
                "PTC Inc share price",
                "Apple stock price",
                "Microsoft stock price",
                "how is Tesla stock doing?"
        )) {
            RequestAnalysis analysis = analyzer.analyze(ask, catalog);
            assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
            assertThat(analysis.allowedTools()).contains("web_search");
            assertThat(analysis.allowedTools()).doesNotContain("execute_java_snippet");
            assertThat(knowledge(analysis)).isEqualTo(KnowledgeRequirement.CURRENT_KNOWLEDGE.name());
        }
    }

    @Test
    void currentInformationRoutesToWebNeverJava() {
        for (String ask : List.of(
                "tell me about recent layoffs at a company",
                "latest AI news",
                "recent developments in RAG",
                "what happened recently at a company?"
        )) {
            RequestAnalysis analysis = analyzer.analyze(ask, catalog);
            assertThat(analysis.intent()).isIn(Intent.WEB_SEARCH, Intent.WEB_RESEARCH);
            assertThat(analysis.allowedTools()).contains("web_search");
            assertThat(analysis.allowedTools()).doesNotContain("execute_java_snippet");
        }
    }

    @Test
    void weatherAndTrafficStayOnWeb() {
        assertThat(analyzer.analyze("weather today in Pune", catalog).intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analyzer.analyze("traffic right now between two locations", catalog).intent())
                .isEqualTo(Intent.WEB_SEARCH);
        assertThat(analyzer.analyze("will it rain today in Pune", catalog).allowedTools())
                .contains("web_search")
                .doesNotContain("execute_java_snippet");
    }

    @Test
    void javaExecutionIsOnlyForRunRequests() {
        RequestAnalysis run = analyzer.analyze("execute this Java snippet", catalog);
        assertThat(run.allowedTools()).contains("execute_java_snippet");
        RequestAnalysis explain = analyzer.analyze("inner join vs outer join", catalog);
        assertThat(explain.allowedTools()).doesNotContain("execute_java_snippet");
    }

    @Test
    void runtimeInspectionDoesNotUseJavaSnippet() {
        RequestAnalysis analysis = analyzer.analyze("which Java version is installed?", catalog);
        assertThat(analysis.allowedTools()).doesNotContain("execute_java_snippet");
    }

    @Test
    void independentLookupDoesNotInheritPreviousCurrentTopic() {
        DeskContext previous = DeskContext.of(
                null,
                "recent restructuring news",
                "About 7,300 roles were affected."
        );
        RequestAnalysis second = analyzer.analyze(null, "stripe", catalog, previous);
        assertThat(second.classification().flag("isFollowUp")).isFalse();
        assertThat(String.valueOf(second.classification().entity("query"))).doesNotContain("restructuring");
        assertThat(second.allowedTools()).contains("web_search");
        assertThat(second.allowedTools()).doesNotContain("execute_java_snippet");
    }

    @Test
    void weatherThenWorkTokenDoesNotKeepWeather() {
        RequestAnalysis second = analyzer.analyze(
                null,
                "Jira",
                catalog,
                DeskContext.of(null, "weather in Pune", "28C and cloudy.")
        );
        assertThat(second.classification().flag("isFollowUp")).isFalse();
        assertThat(String.valueOf(second.classification().entity("query"))).doesNotContainIgnoringCase("weather");
    }

    @Test
    void marketThenStaticDoesNotKeepMarket() {
        RequestAnalysis second = analyzer.analyze(
                null,
                "inner join vs outer join",
                catalog,
                DeskContext.of(null, "current share price", "The quote is unavailable.")
        );
        assertThat(second.intent()).isEqualTo(Intent.CHAT);
        assertThat(second.allowedTools()).doesNotContain("web_search", "execute_java_snippet");
        assertThat(second.classification().flag("isFollowUp")).isFalse();
    }

    @Test
    void punctuationOnlyAsksForClarification() {
        RequestAnalysis second = analyzer.analyze(
                null,
                "***",
                catalog,
                DeskContext.of(null, "recent restructuring news", "About 7,300 roles.")
        );
        assertThat(second.skipModel()).isTrue();
        assertThat(second.classification().flag("isFollowUp")).isFalse();
        assertThat(second.skipMessage()).contains("question");
        assertThat(second.allowedTools()).isEmpty();
    }

    @Test
    void quantityFollowUpRetrievesPreviousTopic() {
        RequestAnalysis second = analyzer.analyze(
                null,
                "how many employees were laid off?",
                catalog,
                DeskContext.of(null, "recent restructuring news", "About 7,300 roles were affected.")
        );
        assertThat(second.classification().flag("isFollowUp")).isTrue();
        assertThat(second.classification().entity("reference")).isEqualTo("PREVIOUS_TOPIC");
        assertThat(second.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(String.valueOf(second.classification().entity("query"))).contains("restructuring");
        assertThat(second.allowedTools()).doesNotContain("execute_java_snippet");
    }

    @Test
    void sourceFollowUpRetrievesRatherThanRepeating() {
        RequestAnalysis second = analyzer.analyze(
                null,
                "source?",
                catalog,
                DeskContext.of(null, "recent restructuring news", "About 7,300 roles were affected.")
        );
        assertThat(second.classification().flag("isFollowUp")).isTrue();
        assertThat(second.classification().entity("reference")).isEqualTo("SOURCE_VERIFICATION");
        assertThat(second.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(second.classification().flag("reuseContext")).isFalse();
    }

    @Test
    void checkAgainIsFreshDataOnPreviousLiveTopic() {
        RequestAnalysis second = analyzer.analyze(
                null,
                "check traffic again",
                catalog,
                DeskContext.of(
                        null,
                        "traffic from Wadgaon Sheri to Yerwada now",
                        "Heavy traffic on that route."
                )
        );
        assertThat(second.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(second.classification().flag("freshnessOverride")
                || second.classification().flag("isFollowUp")).isTrue();
    }

    private static String knowledge(RequestAnalysis analysis) {
        Object value = analysis.classification() == null
                ? null
                : analysis.classification().entity("knowledgeRequirement");
        return value == null ? "" : value.toString();
    }
}
