package com.kesh.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.config.SystemPromptProvider;
import com.kesh.llm.LlmResponse;
import com.kesh.llm.ScriptedLlmClient;
import com.kesh.llm.ToolCallParser;
import com.kesh.nlp.Intent;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.Risk;
import com.kesh.nlp.capability.CapabilityDomain;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.observability.AgentAuditLogger;
import com.kesh.routing.RoutingPlan;
import com.kesh.routing.RoutingStep;
import com.kesh.security.ApprovalManager;
import com.kesh.security.ToolPermissionPolicy;
import com.kesh.tools.FakeTool;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.ToolCall;
import com.kesh.tools.ToolRegistry;

class AgentLoopTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private KeshProperties properties;
    private FakeTool search;
    private FakeTool read;
    private ToolRegistry registry;
    private ApprovalManager approvals;
    private AgentLoop loop;

    @BeforeEach
    void setUp() {
        properties = new KeshProperties();
        properties.getAgent().setMaxIterations(10);
        properties.getLlm().setModel("gemma3");
        search = new FakeTool("search_files", PermissionLevel.READ).succeeding("UserService.java");
        read = new FakeTool("read_file", PermissionLevel.READ).succeeding("class UserService {}");
        registry = new ToolRegistry(
                List.of(search, read),
                new SystemPromptProvider(new ByteArrayResource("You are Kesh.".getBytes()))
        );
        approvals = new ApprovalManager();
        loop = new AgentLoop(
                new ScriptedLlmClient(),
                registry,
                new ToolPermissionPolicy(properties),
                approvals,
                new ToolCallParser(mapper),
                new AgentAuditLogger(),
                properties
        );
    }

    @Test
    void simpleResponseWithoutTools() {
        ScriptedLlmClient llm = new ScriptedLlmClient().then(LlmResponse.text("Hello from Kesh"));
        AgentLoop agent = agent(llm);
        AgentResponse response = agent.run(context(), AgentEventListener.noop());
        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(response.message()).contains("Hello from Kesh");
        assertThat(search.executions()).isZero();
    }

    @Test
    void oneToolCallThenAnswer() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("<tool_call>{\"tool\":\"search_files\",\"arguments\":{\"path\":\"/tmp\",\"query\":\"x\"}}</tool_call>",
                        List.of(ToolCall.of("search_files", Map.of("query", "x")))))
                .then(LlmResponse.text("Found UserService.java"));
        AgentResponse response = agent(llm).run(context(), AgentEventListener.noop());
        assertThat(search.executions()).isEqualTo(1);
        assertThat(response.message()).contains("Found UserService.java");
    }

    @Test
    void multipleSequentialToolCalls() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("call search", List.of(ToolCall.of("search_files", Map.of("query", "KafkaConsumer")))))
                .then(LlmResponse.tools("call read", List.of(ToolCall.of("read_file", Map.of("path", "UserService.java")))))
                .then(LlmResponse.text("It consumes Kafka events."));
        AgentResponse response = agent(llm).run(context(), AgentEventListener.noop());
        assertThat(search.executions()).isEqualTo(1);
        assertThat(read.executions()).isEqualTo(1);
        assertThat(response.message()).contains("consumes Kafka");
    }

    @Test
    void stopsAtMaxIterations() {
        properties.getAgent().setMaxIterations(2);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("a", List.of(ToolCall.of("search_files", Map.of("query", "a")))))
                .then(LlmResponse.tools("b", List.of(ToolCall.of("search_files", Map.of("query", "b")))))
                .then(LlmResponse.text("should not reach"));
        AgentResponse response = agent(llm).run(context(), AgentEventListener.noop());
        assertThat(response.status()).isEqualTo(AgentResponse.Status.MAX_ITERATIONS);
        assertThat(search.executions()).isEqualTo(2);
    }

    @Test
    void malformedToolCallIsRetried() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("{\"tool\": "))
                .then(LlmResponse.text("I could not parse that. Try again?"));
        AgentResponse response = agent(llm).run(context(), AgentEventListener.noop());
        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(search.executions()).isZero();
        assertThat(response.message()).contains("could not parse");
    }

    @Test
    void fakeWebSearchXmlIsNotShownOrExecuted() {
        FakeTool web = new FakeTool("web_search", PermissionLevel.READ).succeeding("should not run");
        registry.register(web);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("""
                        <web_search>
                        {"query": "AI system design", "max_results": 5}
                        </web_search>
                        """))
                .then(LlmResponse.text("AI system design is how you structure models, data, and tools."));
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
                        Map.of("knowledgeRequirement", "STATIC_KNOWLEDGE"),
                        "test"
                ),
                null
        );
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("tell me about AI system design");
        java.util.List<String> streamed = new java.util.ArrayList<>();
        AgentResponse response = agent(llm).run(ctx, event -> {
            if ("token".equals(event.type()) && event.data().get("text") != null) {
                streamed.add(event.data().get("text").toString());
            }
        }, analysis);
        assertThat(web.executions()).isZero();
        assertThat(response.message()).doesNotContain("<web_search>");
        assertThat(response.message()).contains("AI system design");
        assertThat(String.join("", streamed)).doesNotContain("<web_search>");
    }

    @Test
    void toolFailureIsReturnedToModel() {
        search.failing("disk exploded");
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("search", List.of(ToolCall.of("search_files", Map.of("query", "x")))))
                .then(LlmResponse.text("Search failed, please check the path."));
        AgentResponse response = agent(llm).run(context(), AgentEventListener.noop());
        assertThat(response.message()).contains("Search failed");
        assertThat(search.executions()).isEqualTo(1);
    }

    @Test
    void llmFailureSurfacesAsError() {
        ScriptedLlmClient llm = new ScriptedLlmClient().thenFail("connection refused");
        AgentResponse response = agent(llm).run(context(), AgentEventListener.noop());
        assertThat(response.status()).isEqualTo(AgentResponse.Status.ERROR);
        assertThat(response.message()).contains("connection refused");
    }

    @Test
    void writeToolRequiresApproval() {
        FakeTool open = new FakeTool("open_app", PermissionLevel.WRITE).succeeding("opened");
        registry.register(open);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("open", List.of(ToolCall.of("open_app", Map.of("name", "Google Chrome")))));
        AgentResponse response = agent(llm).run(context(), AgentEventListener.noop());
        assertThat(response.status()).isEqualTo(AgentResponse.Status.APPROVAL_REQUIRED);
        assertThat(open.executions()).isZero();
        assertThat(response.approval().tool()).isEqualTo("open_app");
    }

    @Test
    void rejectedApprovalDoesNotExecuteTool() {
        FakeTool open = new FakeTool("open_app", PermissionLevel.WRITE).succeeding("opened");
        registry.register(open);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("open", List.of(ToolCall.of("open_app", Map.of("name", "Google Chrome")))))
                .then(LlmResponse.text("Okay, I will not open Chrome."));
        AgentLoop agent = agent(llm);
        ConversationContext ctx = context();
        AgentResponse pending = agent.run(ctx, AgentEventListener.noop());
        AgentResponse after = agent.resumeRejected(ctx, pending.approval(), AgentEventListener.noop());
        assertThat(open.executions()).isZero();
        assertThat(after.message()).contains("will not open");
    }

    @Test
    void classifierBlocksToolsOutsideAllowedSet() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("search", List.of(ToolCall.of("search_files", Map.of("query", "x")))))
                .then(LlmResponse.text("I will answer without that tool."));
        RequestAnalysis chatOnly = new RequestAnalysis(
                Intent.CHAT, 0.9, Risk.READ, 0.9, List.of(), true, java.util.Set.of(), false, null
        );
        AgentResponse response = agent(llm).run(context(), AgentEventListener.noop(), chatOnly);
        assertThat(search.executions()).isZero();
        assertThat(response.message()).contains("without that tool");
    }

    @Test
    void greetingDoesNotRunClockTool() {
        FakeTool clock = new FakeTool("current_time", PermissionLevel.READ).succeeding("Thursday 15:00");
        registry.register(clock);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("clock", List.of(ToolCall.of("current_time", Map.of()))))
                .then(LlmResponse.text("Hello. How can I help?"));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("hi");
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop());
        assertThat(clock.executions()).isZero();
        assertThat(response.message()).contains("Hello");
    }

    @Test
    void whyCurrentTimeDoesNotRunClockTool() {
        FakeTool clock = new FakeTool("current_time", PermissionLevel.READ).succeeding("Thursday 15:00");
        registry.register(clock);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("clock", List.of(ToolCall.of("current_time", Map.of()))))
                .then(LlmResponse.text("I should not have called that tool."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("why current time?");
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop());
        assertThat(clock.executions()).isZero();
        assertThat(response.message()).contains("should not have called");
    }

    @Test
    void clockRunsWhenUserAsks() {
        FakeTool clock = new FakeTool("current_time", PermissionLevel.READ).succeeding("Thursday 15:00");
        registry.register(clock);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("clock", List.of(ToolCall.of("current_time", Map.of()))))
                .then(LlmResponse.text("It is Thursday 15:00"));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("what time is it");
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop());
        assertThat(clock.executions()).isEqualTo(1);
        assertThat(response.message()).contains("Thursday 15:00");
    }

    @Test
    void inspectsJavaVersionWhenAsked() {
        FakeTool inspect = new FakeTool("inspect_runtime", PermissionLevel.READ)
                .succeeding("Java (java -version):\nopenjdk version \"21.0.9\"");
        registry.register(inspect);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("This Mac has OpenJDK 21.0.9."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("tell me what version of java installed on this mac");
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.CHAT, 0.9, Risk.READ, 0.9, List.of(), true, java.util.Set.of("inspect_runtime"), false, null
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(inspect.executions()).isEqualTo(1);
        assertThat(inspect.calls().get(0)).containsEntry("name", "java");
        assertThat(response.message()).contains("21.0.9");
    }

    @Test
    void yesAfterCursorOfferPrefetchesCursorInspectNotWeb() {
        FakeTool inspect = new FakeTool("inspect_runtime", PermissionLevel.READ)
                .succeeding("Cursor 1.7.0");
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ)
                .succeeding("Found 5 sources about Yes the band");
        registry.register(inspect);
        registry.register(search);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("Cursor 1.7.0 is installed on this Mac."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("cursor");
        ctx.addUser("yes");
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.INSPECT,
                0.93,
                Risk.READ,
                0.9,
                List.of(),
                true,
                java.util.Set.of("inspect_runtime"),
                false,
                null,
                com.kesh.nlp.capability.ClassificationResult.single(
                        CapabilityIntent.RUNTIME_INSPECT,
                        0.93,
                        Map.of("runtime", "cursor", "query", "cursor version installed on this mac"),
                        "turn-continuation"
                ),
                new RoutingPlan(List.of(
                        new RoutingStep(CapabilityDomain.SYSTEM, CapabilityIntent.RUNTIME_INSPECT, "cursor version installed on this mac", "cursor", 0.93)
                ), false, null)
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(inspect.executions()).isEqualTo(1);
        assertThat(inspect.calls().get(0)).containsEntry("name", "cursor");
        assertThat(search.executions()).isZero();
        assertThat(response.message()).contains("1.7.0");
    }

    @Test
    void composedPlanPrefetchesInspectAndWeb() {
        FakeTool inspect = new FakeTool("inspect_runtime", PermissionLevel.READ)
                .succeeding("Java (java -version):\nopenjdk version \"21.0.9\"");
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ)
                .succeeding("Found 1 sources:\n1. JDK 25\n   https://openjdk.org");
        registry.register(inspect);
        registry.register(search);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("This Mac has 21. The latest public JDK is 25."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("is my Java newer than the latest release?");
        RoutingPlan plan = new RoutingPlan(List.of(
                new RoutingStep(CapabilityDomain.SYSTEM, CapabilityIntent.RUNTIME_INSPECT, "java", "java", 0.9),
                new RoutingStep(CapabilityDomain.WEB, CapabilityIntent.WEB_SEARCH, "latest Java version", null, 0.9),
                new RoutingStep(CapabilityDomain.LLM, CapabilityIntent.LLM_EXPLAIN, "is my Java newer than the latest release?", null, 0.9)
        ), false, null);
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.INSPECT,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                true,
                java.util.Set.of("inspect_runtime", "web_search"),
                false,
                null,
                null,
                plan
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(inspect.executions()).isEqualTo(1);
        assertThat(search.executions()).isEqualTo(1);
        assertThat(inspect.calls().get(0)).containsEntry("name", "java");
        assertThat(search.calls().get(0)).containsEntry("query", "latest Java version");
        assertThat(response.message()).contains("21");
    }

    @Test
    void webSearchToolThenAnswer() {
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ)
                .succeeding("Found 1 sources:\n1. Spring\n   https://spring.io");
        registry.register(search);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("Spring Boot 3.5 is current."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("What's the latest Spring Boot release?");
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.WEB_SEARCH, 0.9, Risk.READ, 0.9, List.of(), true, java.util.Set.of("web_search"), false, null
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(search.executions()).isEqualTo(1);
        assertThat(search.calls().get(0)).containsEntry("query", "What's the latest Spring Boot release?");
        assertThat(response.message()).contains("3.5");
        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
    }

    @Test
    void extraWebSearchAfterHitIsSkippedAndAnswered() {
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ)
                .succeeding("Found 1 sources:\n1. Oracle layoffs\n   https://example.com/oracle");
        registry.register(search);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools(
                        "<tool_call>{\"tool\":\"web_search\",\"arguments\":{\"query\":\"oracle layoff\"}}</tool_call>",
                        List.of(ToolCall.of("web_search", Map.of("query", "oracle layoff")))
                ))
                .then(LlmResponse.tools(
                        "<tool_call>{\"tool\":\"web_search\",\"arguments\":{\"query\":\"yes band\"}}</tool_call>",
                        List.of(ToolCall.of("web_search", Map.of("query", "yes band")))
                ))
                .then(LlmResponse.text("Oracle announced job cuts."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("oracle layoff");
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.WEB_SEARCH, 0.9, Risk.READ, 0.9, List.of(), true, java.util.Set.of("web_search"), false, null
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(search.executions()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(response.message()).contains("job cuts");
        assertThat(response.message()).doesNotContain("maximum number of tool steps");
    }

    @Test
    void weatherPrefetchEvenIfModelRefuses() {
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ)
                .succeeding("Found 1 sources:\n1. AccuWeather Pune\n   28°C partly cloudy");
        registry.register(search);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("I cannot check the weather in Pune. I do not have the ability to access external services like weather APIs."))
                .then(LlmResponse.text("It is 28°C and partly cloudy in Pune."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("can you check weather in Pune");
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.WEB_SEARCH, 0.9, Risk.READ, 0.9, List.of(), true, java.util.Set.of("web_search"), false, null
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(search.executions()).isEqualTo(1);
        assertThat(search.calls().get(0)).containsEntry("query", "can you check weather in Pune");
        assertThat(response.message()).contains("28°C");
        assertThat(response.message()).doesNotContain("cannot check");
    }

    @Test
    void webResearchSearchThenFetch() {
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ).succeeding("https://kafka.apache.org");
        FakeTool fetch = new FakeTool("web_fetch", PermissionLevel.READ).succeeding("title: Kafka\nExactly-once exists.");
        registry.register(search);
        registry.register(fetch);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("f", List.of(ToolCall.of("web_fetch", Map.of("url", "https://kafka.apache.org")))))
                .then(LlmResponse.text("Kafka supports exactly-once in the docs."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("Research the current state of Kafka exactly-once processing.");
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.WEB_RESEARCH,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                true,
                java.util.Set.of("web_search", "web_fetch", "web_crawl"),
                false,
                null
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(search.executions()).isEqualTo(1);
        assertThat(fetch.executions()).isEqualTo(1);
        assertThat(response.message()).contains("exactly-once");
    }

    @Test
    void webToolFailureStillAnswers() {
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ).failing("Search HTTP 502");
        registry.register(search);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("The web search failed, so I cannot confirm the latest release."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("What's the latest Spring Boot release?");
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.WEB_SEARCH, 0.9, Risk.READ, 0.9, List.of(), true, java.util.Set.of("web_search"), false, null
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(search.executions()).isEqualTo(1);
        assertThat(response.message()).contains("failed");
    }

    @Test
    void webAnswerDropsInlineSourceList() {
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ)
                .succeeding("Found 2 sources:\n1. Oracle layoffs\n   https://www.nytimes.com/oracle");
        registry.register(search);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("""
                        Oracle is cutting about 10,000 jobs.

                        Source 1: [https://www.nytimes.com/2024/02/23/technology/oracle-layoffs-restructuring.html](https://www.nytimes.com/2024/02/23/technology/oracle-layoffs-restructuring.html) - The New York Times reports that Oracle is cutting 10,000 jobs.
                        Source 2: [https://www.reuters.com/technology/oracle-cut-10000-jobs-part-restructuring-plan-2024-02-23/](https://www.reuters.com/technology/oracle-cut-10000-jobs-part-restructuring-plan-2024-02-23/) - Reuters confirms the 10,000 job cuts.
                        """));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("oracle layoff");
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.WEB_SEARCH, 0.9, Risk.READ, 0.9, List.of(), true, java.util.Set.of("web_search"), false, null
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(search.executions()).isEqualTo(1);
        assertThat(response.message()).contains("10,000");
        assertThat(response.message()).doesNotContain("Source 1");
        assertThat(response.message()).doesNotContain("nytimes.com");
    }

    @Test
    void incompatibleJavaIsNotExecutedOrRetried() {
        FakeTool javaTool = new FakeTool("execute_java_snippet", PermissionLevel.DANGEROUS).succeeding("42");
        FakeTool search = new FakeTool("web_search", PermissionLevel.READ)
                .succeeding("Found 1 sources:\n1. Quote\n   last 180.12");
        registry.register(javaTool);
        registry.register(search);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("java", List.of(ToolCall.of("execute_java_snippet", Map.of("code", "1")))))
                .then(LlmResponse.tools("java2", List.of(ToolCall.of("execute_java_snippet", Map.of("code", "2")))))
                .then(LlmResponse.text("The last quote is 180.12"));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("current share price");
        RequestAnalysis analysis = new RequestAnalysis(
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
                        Map.of("knowledgeRequirement", "CURRENT_KNOWLEDGE", "query", "current share price"),
                        "test"
                ),
                new RoutingPlan(List.of(
                        new RoutingStep(CapabilityDomain.WEB, CapabilityIntent.WEB_SEARCH, "current share price", null, 0.9)
                ), false, null)
        );
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(javaTool.executions()).isZero();
        assertThat(search.executions()).isEqualTo(1);
        assertThat(response.message()).doesNotContain("malformed");
        assertThat(response.message()).doesNotContain("execute_java_snippet");
        assertThat(response.message()).doesNotContain("<tool_call>");
    }

    @Test
    void malformedProtocolIsNeverShownToTheUser() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("Your last tool call was malformed. Respond with a valid <tool_call> JSON block or answer the user."))
                .then(LlmResponse.text("A join keeps matching rows from both tables."));
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("inner join vs outer join");
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop());
        assertThat(response.message()).doesNotContain("malformed");
        assertThat(response.message()).doesNotContain("<tool_call>");
        assertThat(response.message()).contains("join");
    }

    @Test
    void policyDeniedToolIsNotRetried() {
        properties.getJavaExecution().setEnabled(false);
        FakeTool javaTool = new FakeTool("execute_java_snippet", PermissionLevel.DANGEROUS).succeeding("nope");
        registry.register(javaTool);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.tools("java", List.of(ToolCall.of("execute_java_snippet", Map.of("code", "1")))))
                .then(LlmResponse.tools("java2", List.of(ToolCall.of("execute_java_snippet", Map.of("code", "2")))))
                .then(LlmResponse.text("I will not run Java for that."));
        RequestAnalysis analysis = new RequestAnalysis(
                Intent.JAVA_SNIPPET, 0.9, Risk.READ, 0.9, List.of(), true, Set.of("execute_java_snippet"), false, null
        );
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("run this Java code");
        AgentResponse response = agent(llm).run(ctx, AgentEventListener.noop(), analysis);
        assertThat(javaTool.executions()).isZero();
        assertThat(response.message()).doesNotContain("malformed");
    }

    private AgentLoop agent(ScriptedLlmClient llm) {
        return new AgentLoop(
                llm,
                registry,
                new ToolPermissionPolicy(properties),
                approvals,
                new ToolCallParser(mapper),
                new AgentAuditLogger(),
                properties
        );
    }

    private ConversationContext context() {
        ConversationContext ctx = new ConversationContext("c1", "You are Kesh.");
        ctx.addUser("hello");
        return ctx;
    }
}
