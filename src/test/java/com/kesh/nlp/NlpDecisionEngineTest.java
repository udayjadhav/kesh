package com.kesh.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.routing.CapabilityRouter;
import com.kesh.tools.FakeTool;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;

class NlpDecisionEngineTest {

    private NlpDecisionEngine engine;
    private List<Tool> catalog;

    @BeforeEach
    void setUp() {
        engine = new NlpDecisionEngine(new KeshProperties());
        catalog = List.of(
                new FakeTool("list_directory", PermissionLevel.READ),
                new FakeTool("inspect_runtime", PermissionLevel.READ),
                new FakeTool("current_time", PermissionLevel.READ),
                new FakeTool("run_command", PermissionLevel.DANGEROUS),
                new FakeTool("web_search", PermissionLevel.READ),
                new FakeTool("web_fetch", PermissionLevel.READ),
                new FakeTool("web_crawl", PermissionLevel.READ),
                new FakeTool("execute_java_snippet", PermissionLevel.DANGEROUS)
        );
    }

    @Test
    void chatIntentDoesNotForceInspect() {
        RequestAnalysis analysis = engine.decide(
                null,
                "which cursor version",
                catalog,
                Intent.CHAT,
                0.4,
                Risk.READ,
                0.9,
                List.of("cursor")
        );
        assertThat(analysis.intent()).isEqualTo(Intent.CHAT);
        assertThat(analysis.allowedTools()).isEmpty();
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.hint()).contains("Never paste TOOL_RESULT");
        assertThat(analysis.hint()).contains("Web tools are not allowed this turn");
    }

    @Test
    void inspectIntentAllowsRuntimeTool() {
        RequestAnalysis analysis = engine.decide(
                null,
                "which is mac version",
                catalog,
                Intent.INSPECT,
                0.8,
                Risk.READ,
                0.7,
                List.of()
        );
        assertThat(analysis.intent()).isEqualTo(Intent.INSPECT);
        assertThat(analysis.allowedTools()).containsExactly("inspect_runtime");
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.hint()).contains("inspect_runtime");
        assertThat(analysis.shortHint()).contains("inspect_runtime");
        assertThat(analysis.shortHint()).doesNotContain("Request classifier");
        assertThat(analysis.shortHint()).doesNotContain("Available tools");
    }

    @Test
    void clockAskAllowsCurrentTime() {
        RequestAnalysis analysis = engine.decide(
                null,
                "give me current time",
                catalog,
                Intent.CHAT,
                0.9,
                Risk.READ,
                0.9,
                List.of()
        );
        assertThat(analysis.allowedTools()).containsExactly("current_time");
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void greetingGetsNoTools() {
        RequestAnalysis analysis = engine.decide(
                null,
                "hi",
                catalog,
                Intent.CHAT,
                0.9,
                Risk.READ,
                0.9,
                List.of()
        );
        assertThat(analysis.allowedTools()).isEmpty();
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void terminalSkipStillWinsWhenNotAVersionAsk() {
        RequestAnalysis analysis = engine.decide(
                null,
                "run mvn test",
                catalog,
                Intent.TERMINAL,
                0.9,
                Risk.WRITE,
                0.8,
                List.of()
        );
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).contains("terminal");
    }

    @Test
    void webSearchAllowedWhenEnabled() {
        RequestAnalysis analysis = engine.decide(
                null,
                "What's the latest Spring Boot version?",
                catalog,
                Intent.WEB_SEARCH,
                0.9,
                Risk.READ,
                0.9,
                List.of()
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(analysis.hint()).contains("Never say you cannot access weather APIs");
    }

    @Test
    void webSearchDisabledIsRejected() {
        KeshProperties properties = new KeshProperties();
        properties.getWeb().getSearch().setEnabled(false);
        NlpDecisionEngine closed = new NlpDecisionEngine(properties);
        RequestAnalysis analysis = closed.decide(
                null,
                "What's the latest Spring Boot version?",
                catalog,
                Intent.WEB_SEARCH,
                0.9,
                Risk.READ,
                0.9,
                List.of()
        );
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).containsIgnoringCase("search");
    }

    @Test
    void slackDisconnectedStillStops() {
        RequestAnalysis analysis = engine.decide(
                null,
                "show my unread slack messages",
                catalog,
                Intent.WORK,
                0.9,
                Risk.READ,
                0.9,
                List.of()
        );
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).containsIgnoringCase("Slack");
    }

    @Test
    void personalDeskWorkRequestStops() {
        RequestAnalysis analysis = engine.decide(
                com.kesh.workspace.WorkspaceMode.PERSONAL,
                "show my unread slack messages",
                catalog,
                Intent.WORK,
                0.9,
                Risk.READ,
                0.9,
                List.of()
        );
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).containsIgnoringCase("Slack");
        assertThat(analysis.skipMessage()).doesNotContain("/work");
    }

    @Test
    void javaHelperIsOfferedForLlmWorkNotAsTheUserIntent() {
        ClassificationResult explain = ClassificationResult.single(
                CapabilityIntent.LLM_EXPLAIN, 0.9, java.util.Map.of(), "test"
        );
        var plan = new CapabilityRouter().plan("what is the 100th Fibonacci number", explain);
        RequestAnalysis analysis = engine.decide(
                null,
                "what is the 100th Fibonacci number",
                catalog,
                Intent.CHAT,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                explain,
                plan
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.CHAT);
        assertThat(analysis.allowedTools()).doesNotContain("execute_java_snippet");
        assertThat(analysis.allowedTools()).doesNotContain("run_command");
    }

    @Test
    void javaHelperIsAbsentWhenDisabled() {
        KeshProperties properties = new KeshProperties();
        properties.getJavaExecution().setEnabled(false);
        NlpDecisionEngine disabled = new NlpDecisionEngine(properties);
        ClassificationResult explain = ClassificationResult.single(
                CapabilityIntent.LLM_EXPLAIN, 0.9, java.util.Map.of(), "test"
        );
        var plan = new CapabilityRouter().plan("what is the 100th Fibonacci number", explain);
        RequestAnalysis analysis = disabled.decide(
                null,
                "what is the 100th Fibonacci number",
                catalog,
                Intent.CHAT,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                explain,
                plan
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).doesNotContain("execute_java_snippet");
    }

    @Test
    void writingIntentDoesNotEnableToolsOrSend() {
        ClassificationResult writing = ClassificationResult.single(
                CapabilityIntent.WRITING_DRAFT_EMAIL, 0.9, java.util.Map.of(), "test"
        );
        var plan = new CapabilityRouter().plan("write a recruiter follow-up email", writing);
        RequestAnalysis analysis = engine.decide(
                null,
                "write a recruiter follow-up email",
                catalog,
                Intent.WRITING,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                writing,
                plan
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.WRITING);
        assertThat(analysis.allowedTools()).isEmpty();
        assertThat(analysis.risk()).isEqualTo(Risk.READ);
    }
}
