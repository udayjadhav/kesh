package com.kesh.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.nlp.capability.CapabilityTrainingData;
import com.kesh.nlp.capability.DeskContext;
import com.kesh.nlp.capability.HybridCapabilityClassifier;
import com.kesh.nlp.capability.OpenNlpCapabilityClassifier;
import com.kesh.routing.CapabilityRouter;
import com.kesh.tools.FakeTool;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.workspace.WorkspaceMode;

class RequestAnalyzerTest {

    private RequestAnalyzer analyzer;
    private List<Tool> catalog;
    private KeshProperties properties;

    @BeforeEach
    void setUp() {
        NlpTrainingData data = new NlpTrainingData(
                new ClassPathResource("nlp/intent-train.tsv"),
                new ClassPathResource("nlp/risk-train.tsv")
        );
        properties = new KeshProperties();
        properties.getNlp().getClassifier().setFallbackToLlm(false);
        properties.getNlp().getClassifier().setClarificationEnabled(false);
        CapabilityTrainingData capabilityData = new CapabilityTrainingData(
                new ObjectMapper(),
                new ClassPathResource("nlp/kesh-intents.jsonl"),
                new ClassPathResource("nlp/evaluation.jsonl")
        );
        analyzer = new RequestAnalyzer(
                new HybridCapabilityClassifier(properties, new OpenNlpCapabilityClassifier(capabilityData)),
                new RiskClassifier(data),
                new EntityExtractor(),
                new NlpDecisionEngine(properties),
                new CapabilityRouter(),
                properties
        );
        catalog = List.of(
                new FakeTool("list_directory", PermissionLevel.READ),
                new FakeTool("read_file", PermissionLevel.READ),
                new FakeTool("search_files", PermissionLevel.READ),
                new FakeTool("open_app", PermissionLevel.WRITE),
                new FakeTool("quit_app", PermissionLevel.WRITE),
                new FakeTool("list_running_apps", PermissionLevel.READ),
                new FakeTool("inspect_runtime", PermissionLevel.READ),
                new FakeTool("web_search", PermissionLevel.READ),
                new FakeTool("web_fetch", PermissionLevel.READ),
                new FakeTool("web_crawl", PermissionLevel.READ),
                new FakeTool("execute_java_snippet", PermissionLevel.DANGEROUS)
        );
    }

    @Test
    void greetingDoesNotAllowClockTool() {
        List<Tool> withClock = List.of(
                new FakeTool("list_directory", PermissionLevel.READ),
                new FakeTool("current_time", PermissionLevel.READ)
        );
        RequestAnalysis hi = analyzer.analyze("hi", withClock);
        assertThat(hi.allowedTools()).isEmpty();
        RequestAnalysis why = analyzer.analyze("why current time?", withClock);
        assertThat(why.allowedTools()).isEmpty();
    }

    @Test
    void chatAllowsClockToolOnlyWhenAsked() {
        List<Tool> withClock = List.of(
                new FakeTool("list_directory", PermissionLevel.READ),
                new FakeTool("current_time", PermissionLevel.READ)
        );
        RequestAnalysis analysis = analyzer.analyze("give me current time", withClock);
        assertThat(analysis.allowedTools()).containsExactly("current_time");
        assertThat(analysis.skipModel()).isFalse();
        RequestAnalysis timeNow = analyzer.analyze("time now", withClock);
        assertThat(timeNow.allowedTools()).contains("current_time");
        assertThat(timeNow.skipModel()).isFalse();
        assertThat(timeNow.classification().needsClarification()).isFalse();
    }

    @Test
    void fileRequestAllowsFileTools() {
        RequestAnalysis analysis = analyzer.analyze("read the README.md file in ~/Kesh", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.FILES);
        assertThat(analysis.allowedTools()).contains("read_file", "list_directory", "search_files");
        assertThat(analysis.entities()).anyMatch(value -> value.contains("README.md") || value.contains("Kesh"));
    }

    @Test
    void appRequestAllowsAppTools() {
        RequestAnalysis analysis = analyzer.analyze("open Google Chrome", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.APPS);
        assertThat(analysis.allowedTools()).contains("open_app");
        assertThat(analysis.risk()).isIn(Risk.WRITE, Risk.DANGEROUS);
    }

    @Test
    void javaVersionCheckUsesInspectRuntime() {
        RequestAnalysis analysis = analyzer.analyze(
                "tell me what version of java installed on this mac",
                catalog
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).contains("inspect_runtime");
        assertThat(analysis.allowedTools()).doesNotContain("web_search");
    }

    @Test
    void nodeVersionCheckUsesInspectRuntime() {
        RequestAnalysis analysis = analyzer.analyze("what node version is installed?", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).contains("inspect_runtime");
    }

    @Test
    void cursorVersionCheckUsesInspectRuntime() {
        RequestAnalysis analysis = analyzer.analyze("which cursor version", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).contains("inspect_runtime");
        assertThat(RequestAnalyzer.inferRuntimeName("which cursor version")).isEqualTo("cursor");
    }

    @Test
    void macVersionCheckUsesInspectRuntime() {
        RequestAnalysis analysis = analyzer.analyze("which is mac version", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).contains("inspect_runtime");
        assertThat(RequestAnalyzer.inferRuntimeName("which is mac version")).isEqualTo("macos");
    }

    @Test
    void compareLanguagesIsChatNotInspect() {
        RequestAnalysis analysis = analyzer.analyze(
                "Okay, let’s compare Clojure and Java. Use a markdown table.",
                catalog
        );
        assertThat(analysis.intent()).isNotEqualTo(Intent.INSPECT);
        assertThat(analysis.intent()).isNotEqualTo(Intent.TERMINAL);
        assertThat(analysis.allowedTools()).doesNotContain("inspect_runtime");
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void inspectIntentIsReadRisk() {
        RequestAnalysis analysis = analyzer.analyze("which cursor version", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.INSPECT);
        assertThat(analysis.risk()).isEqualTo(Risk.READ);
    }

    @Test
    void terminalIsStoppedWhenDisabled() {
        RequestAnalysis analysis = analyzer.analyze("run mvn test", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.TERMINAL);
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).contains("terminal");
    }

    @Test
    void workRequestStopsWhenAppsAreNotConnected() {
        RequestAnalysis analysis = analyzer.analyze("show my unread slack messages", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WORK);
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).containsIgnoringCase("Slack");
    }

    @Test
    void workRequestOnPersonalDeskUsesSamePolicy() {
        RequestAnalysis analysis = analyzer.analyze(
                com.kesh.workspace.WorkspaceMode.PERSONAL,
                "show my unread slack messages",
                catalog
        );
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).containsIgnoringCase("Slack");
        assertThat(analysis.skipMessage()).doesNotContain("/work");
    }

    @Test
    void destructiveLooksDangerous() {
        RequestAnalysis analysis = analyzer.analyze("delete this file notes.md", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.DESTRUCTIVE);
        assertThat(analysis.risk()).isEqualTo(Risk.DANGEROUS);
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void latestSpringBootIsWebSearch() {
        RequestAnalysis analysis = analyzer.analyze("What's the latest Spring Boot release?", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.risk()).isEqualTo(Risk.READ);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).contains("web_search");
    }

    @Test
    void latestJavaVersionShortPhraseIsWebSearchNotInspect() {
        RequestAnalysis analysis = analyzer.analyze("latest Java version", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(analysis.allowedTools()).doesNotContain("inspect_runtime");
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void currentJavaReleaseIsWebSearchNotInspect() {
        RequestAnalysis analysis = analyzer.analyze("current Java release", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(RuntimeLexicon.isVersionAsk("current Java release")).isFalse();
    }

    @Test
    void readUrlIsWebFetch() {
        RequestAnalysis analysis = analyzer.analyze("Read https://spring.io and explain it", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_FETCH);
        assertThat(analysis.allowedTools()).contains("web_fetch");
        assertThat(analysis.allowedTools()).doesNotContain("web_search");
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void researchCompanyIsWebResearch() {
        RequestAnalysis analysis = analyzer.analyze(
                "Research CertifyOS and tell me about its technology stack.",
                catalog
        );
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_RESEARCH);
        assertThat(analysis.allowedTools()).contains("web_search", "web_fetch");
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void weatherInPuneIsWebSearch() {
        RequestAnalysis analysis = analyzer.analyze("can you check weather in Pune", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(analysis.allowedTools()).doesNotContain("web_fetch", "web_crawl", "execute_java_snippet");
    }

    @Test
    void oracleLayoffIsOneWebSearchNotAToolLoop() {
        RequestAnalysis analysis = analyzer.analyze("oracle layoff", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(analysis.allowedTools()).doesNotContain("web_fetch", "web_crawl", "execute_java_snippet");
    }

    @Test
    void onePlusOneIsExplainNotWebAndNotClarification() {
        properties.getNlp().getClassifier().setClarificationEnabled(true);
        RequestAnalysis analysis = analyzer.analyze("1+1", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.skipMessage()).isNull();
        assertThat(analysis.intent()).isEqualTo(Intent.CHAT);
        assertThat(analysis.classification().needsClarification()).isFalse();
        assertThat(analysis.allowedTools()).doesNotContain("web_search");
        assertThat(analysis.classification().intent()).isIn(
                com.kesh.nlp.capability.CapabilityIntent.LLM_EXPLAIN,
                com.kesh.nlp.capability.CapabilityIntent.LLM_CODE_HELP,
                com.kesh.nlp.capability.CapabilityIntent.CHAT_GENERAL
        );
    }

    @Test
    void rainChancesFollowsPreviousWeatherSearch() {
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "rain chances?",
                catalog,
                DeskContext.of(null, "how is weather in Pune", "Partly cloudy, 28°C in Pune.")
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(analysis.classification().entities().get("query").toString()).containsIgnoringCase("weather");
    }

    @Test
    void hiAfterWeatherStaysGreeting() {
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "Hi",
                catalog,
                DeskContext.of(null, "how is weather in Pune", "Partly cloudy in Pune.")
        );
        assertThat(analysis.intent()).isEqualTo(Intent.CHAT);
        assertThat(analysis.allowedTools()).doesNotContain("web_search");
    }

    @Test
    void harleyPriceIsNewWebSearchNotPreviousRainQuery() {
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "Harley 440T price in Pune",
                catalog,
                DeskContext.of(
                        null,
                        "rain chances?",
                        "Partly cloudy in Pune. Do you want the hourly forecast?"
                )
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.classification().needsClarification()).isFalse();
        assertThat(analysis.classification().entities().get("query").toString())
                .containsIgnoringCase("Harley")
                .doesNotContain("rain chances");
    }

    @Test
    void yesAfterCursorInstallOfferInspectsCursorNotTheWeb() {
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "yes",
                catalog,
                DeskContext.of(
                        null,
                        "cursor",
                        "Would you like me to check the version of Cursor installed on this Mac?"
                )
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.INSPECT);
        assertThat(analysis.allowedTools()).contains("inspect_runtime");
        assertThat(analysis.allowedTools()).doesNotContain("web_search");
        assertThat(analysis.classification().entities().get("runtime")).isEqualTo("cursor");
        assertThat(analysis.classification().entities().get("query").toString())
                .containsIgnoringCase("cursor")
                .doesNotContainIgnoringCase("yes");
    }

    @Test
    void yesAloneDoesNotSearchTheWeb() {
        RequestAnalysis analysis = analyzer.analyze("yes", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.CHAT);
        assertThat(analysis.allowedTools()).doesNotContain("web_search");
    }

    @Test
    void yesAfterWeatherKeepsTheWeatherQuery() {
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "yes",
                catalog,
                DeskContext.of(null, "how is weather in Pune", "Partly cloudy in Pune. Want the hourly forecast?")
        );
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(analysis.classification().entities().get("query").toString())
                .containsIgnoringCase("weather")
                .doesNotContainIgnoringCase("yes");
    }

    @Test
    void singleWordCompanyLooksUpTheWeb() {
        properties.getNlp().getClassifier().setClarificationEnabled(true);
        RequestAnalysis company = analyzer.analyze("stripe", catalog);
        assertThat(company.skipModel()).isFalse();
        assertThat(company.classification().needsClarification()).isFalse();
        assertThat(company.allowedTools()).contains("web_search");
        RequestAnalysis infosys = analyzer.analyze("infosys", catalog);
        assertThat(infosys.skipModel()).isFalse();
        assertThat(infosys.classification().needsClarification()).isFalse();
        assertThat(infosys.allowedTools()).contains("web_search");
        RequestAnalysis zendesk = analyzer.analyze("zendesk", catalog);
        assertThat(zendesk.skipModel()).isFalse();
        assertThat(zendesk.allowedTools()).contains("web_search");
        RequestAnalysis stock = analyzer.analyze("Infosys stock price", catalog);
        assertThat(stock.skipModel()).isFalse();
        assertThat(stock.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(stock.allowedTools()).contains("web_search");
    }

    @Test
    void openBbeditAfterGmailSkipIsAppNotGmail() {
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "open bbedit",
                catalog,
                DeskContext.of(
                        null,
                        "gmail",
                        "This looks like a Gmail request. That app is not connected. Set the env vars in the README and enable kesh.integrations.<app>.enabled."
                )
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.APPS);
        assertThat(analysis.skipMessage()).isNull();
        assertThat(analysis.allowedTools()).contains("open_app");
    }

    @Test
    void closeChromeAfterGmailSkipIsAppNotGmail() {
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "close chrome",
                catalog,
                DeskContext.of(
                        null,
                        "gmail",
                        "This looks like a Gmail request. That app is not connected."
                )
        );
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.APPS);
        assertThat(analysis.allowedTools()).contains("quit_app");
    }

    @Test
    void unreadGmailStillStopsWhenNotConnected() {
        RequestAnalysis analysis = analyzer.analyze("check unread gmail", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WORK);
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).containsIgnoringCase("Gmail");
    }

    @Test
    void clojureHelloWorldIsCodeHelpNotWebDump() {
        RequestAnalysis analysis = analyzer.analyze("clojure hello world program", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.classification().needsClarification()).isFalse();
        assertThat(analysis.allowedTools()).doesNotContain("web_search");
    }

    @Test
    void howIsWeatherInPuneIsWebSearch() {
        RequestAnalysis analysis = analyzer.analyze("how is weather in Pune", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.allowedTools()).contains("web_search");
    }

    @Test
    void helloWorldJavaProgramIsCodeHelpNotInspect() {
        RequestAnalysis analysis = analyzer.analyze("Hello world Java program", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.skipMessage()).isNull();
        assertThat(analysis.intent()).isNotEqualTo(Intent.INSPECT);
        assertThat(analysis.intent()).isNotEqualTo(Intent.JAVA_SNIPPET);
        assertThat(analysis.allowedTools()).doesNotContain("inspect_runtime");
        assertThat(analysis.allowedTools()).doesNotContain("run_command");
        assertThat(analysis.classification().intent().name()).isIn("LLM_CODE_HELP", "LLM_EXPLAIN", "CHAT_GENERAL");
    }

    @Test
    void llmWorkDoesNotOfferJavaHelper() {
        RequestAnalysis analysis = analyzer.analyze("what is the 100th Fibonacci number", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.intent()).isNotEqualTo(Intent.JAVA_SNIPPET);
        assertThat(analysis.intent()).isNotEqualTo(Intent.TERMINAL);
        assertThat(analysis.allowedTools()).doesNotContain("execute_java_snippet");
        assertThat(analysis.allowedTools()).doesNotContain("run_command");
    }

    @Test
    void runThisJavaCodeOffersJavaExecution() {
        RequestAnalysis analysis = analyzer.analyze("run this Java code", catalog);
        assertThat(analysis.allowedTools()).contains("execute_java_snippet");
        assertThat(analysis.allowedTools()).doesNotContain("run_command");
    }

    @Test
    void weatherTypoStillSearches() {
        RequestAnalysis analysis = analyzer.analyze("hats the weather in Pune", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).contains("web_search");
    }

    @Test
    void clarificationReplyWebSearchUsesOriginalQuestion() {
        KeshProperties properties = new KeshProperties();
        properties.getNlp().getClassifier().setFallbackToLlm(false);
        properties.getNlp().getClassifier().setClarificationEnabled(true);
        RequestAnalyzer gated = new RequestAnalyzer(
                new HybridCapabilityClassifier(properties, new OpenNlpCapabilityClassifier(
                        new CapabilityTrainingData(
                                new ObjectMapper(),
                                new ClassPathResource("nlp/kesh-intents.jsonl"),
                                new ClassPathResource("nlp/evaluation.jsonl")
                        )
                )),
                new RiskClassifier(new NlpTrainingData(
                        new ClassPathResource("nlp/intent-train.tsv"),
                        new ClassPathResource("nlp/risk-train.tsv")
                )),
                new EntityExtractor(),
                new NlpDecisionEngine(properties),
                new CapabilityRouter(),
                properties
        );
        RequestAnalysis follow = gated.analyze(
                null,
                "web search",
                catalog,
                DeskContext.of(
                        null,
                        "hats the weather in Pune",
                        "I am not sure what you want. Do you want a local machine check, a file/project look-up, a web search, or just an explanation?"
                )
        );
        assertThat(follow.skipModel()).isFalse();
        assertThat(follow.intent()).isEqualTo(Intent.WEB_SEARCH);
        assertThat(follow.allowedTools()).contains("web_search");
        assertThat(follow.classification().entities().get("query").toString()).containsIgnoringCase("weather");
        assertThat(follow.plan().steps().getFirst().query()).containsIgnoringCase("Pune");
    }

    @Test
    void explainRestStaysChat() {
        RequestAnalysis analysis = analyzer.analyze("Explain REST", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.CHAT);
        assertThat(analysis.allowedTools()).doesNotContain("run_command", "inspect_runtime");
    }

    @Test
    void whatIsKafkaStaysChat() {
        RequestAnalysis analysis = analyzer.analyze("What is Kafka?", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.CHAT);
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void howToRunMvnIsHelpNotTerminal() {
        RequestAnalysis analysis = analyzer.analyze("Tell me how to run mvn test", catalog);
        assertThat(analysis.intent()).isNotEqualTo(Intent.TERMINAL);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.allowedTools()).doesNotContain("run_command");
    }

    @Test
    void howToOpenChromeIsHelpNotAppOpen() {
        RequestAnalysis analysis = analyzer.analyze("How do I open Chrome programmatically?", catalog);
        assertThat(analysis.intent()).isNotEqualTo(Intent.APPS);
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void curlUrlStaysTerminalStop() {
        RequestAnalysis analysis = analyzer.analyze("Run curl https://example.com", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.TERMINAL);
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).contains("terminal");
    }

    @Test
    void searchJiraIsStillWorkNotWeb() {
        RequestAnalysis analysis = analyzer.analyze("Search Jira", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WORK);
        assertThat(analysis.skipModel()).isTrue();
        assertThat(analysis.skipMessage()).containsIgnoringCase("Jira");
    }

    @Test
    void disabledNlpDoesNotGate() {
        KeshProperties properties = new KeshProperties();
        properties.getNlp().setEnabled(false);
        NlpTrainingData data = new NlpTrainingData(
                new ClassPathResource("nlp/intent-train.tsv"),
                new ClassPathResource("nlp/risk-train.tsv")
        );
        RequestAnalyzer open = new RequestAnalyzer(
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
        RequestAnalysis analysis = open.analyze("run mvn test", catalog);
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.restrictTools()).isFalse();
    }

    @Test
    void grammarFixIsWritingWithoutTools() {
        RequestAnalysis analysis = analyzer.analyze("Fix the grammar in this.", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WRITING);
        assertThat(analysis.classification().intent()).isEqualTo(com.kesh.nlp.capability.CapabilityIntent.WRITING_CORRECT_GRAMMAR);
        assertThat(analysis.allowedTools()).isEmpty();
        assertThat(analysis.skipModel()).isFalse();
        assertThat(analysis.risk()).isEqualTo(Risk.READ);
    }

    @Test
    void howToWriteEmailIsExplainNotDraft() {
        RequestAnalysis analysis = analyzer.analyze("How do I write a professional email?", catalog);
        assertThat(analysis.intent()).isNotEqualTo(Intent.WRITING);
        assertThat(analysis.classification().intent()).isEqualTo(com.kesh.nlp.capability.CapabilityIntent.LLM_EXPLAIN);
    }

    @Test
    void jiraCommentDraftIsWritingNotWorkLookup() {
        RequestAnalysis analysis = analyzer.analyze("turn this into a Jira comment", catalog);
        assertThat(analysis.intent()).isEqualTo(Intent.WRITING);
        assertThat(analysis.classification().intent()).isEqualTo(com.kesh.nlp.capability.CapabilityIntent.WRITING_DRAFT_JIRA);
        assertThat(analysis.skipModel()).isFalse();
    }

    @Test
    void personalDeskStillGetsWritingWithoutWorkTools() {
        RequestAnalysis personal = analyzer.analyze(
                WorkspaceMode.PERSONAL,
                "Fix the grammar in this.",
                catalog
        );
        assertThat(personal.intent()).isEqualTo(Intent.WRITING);
        assertThat(personal.allowedTools()).isEmpty();
        assertThat(personal.risk()).isEqualTo(Risk.READ);
    }

    @Test
    void formatMessageIsWritingFormatWithoutTools() {
        RequestAnalysis analysis = analyzer.analyze(
                "Hi Team ,\nNot feelin well , day off\n\nformat this message",
                catalog
        );
        assertThat(analysis.intent()).isEqualTo(Intent.WRITING);
        assertThat(analysis.classification().intent()).isEqualTo(
                com.kesh.nlp.capability.CapabilityIntent.WRITING_FORMAT
        );
        assertThat(analysis.allowedTools()).isEmpty();
    }

    @Test
    void tellMeAboutAiSystemDesignDoesNotSearch() {
        RequestAnalysis analysis = analyzer.analyze("tell me about AI system design", catalog);
        assertThat(analysis.allowedTools()).doesNotContain("web_search", "web_fetch", "web_crawl");
        assertThat(analysis.classification().intent()).isEqualTo(
                com.kesh.nlp.capability.CapabilityIntent.LLM_EXPLAIN
        );
        assertThat(analysis.classification().entity("knowledgeRequirement"))
                .isEqualTo("STATIC_KNOWLEDGE");
    }

    @Test
    void latestAiPatternsAllowWebSearch() {
        RequestAnalysis analysis = analyzer.analyze("what are the latest AI system design patterns?", catalog);
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(analysis.classification().entity("knowledgeRequirement"))
                .isIn("CURRENT_KNOWLEDGE", "EXTERNAL_RESEARCH");
    }

    @Test
    void searchTheWebIsExplicitWeb() {
        RequestAnalysis analysis = analyzer.analyze("search the web for AI system design", catalog);
        assertThat(analysis.allowedTools()).contains("web_search");
    }

    @Test
    void summarizeAboveAfterTrafficDoesNotSearchAgain() {
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "can you summarize above response",
                catalog,
                DeskContext.of(
                        null,
                        "traffic in Pune between Wadgaon Sheri to Yerwada now",
                        "Traffic is currently heavy between Wadgaon Sheri and Yerwada."
                )
        );
        assertThat(analysis.intent()).isEqualTo(Intent.WRITING);
        assertThat(analysis.classification().intent()).isEqualTo(
                com.kesh.nlp.capability.CapabilityIntent.WRITING_SUMMARIZE
        );
        assertThat(analysis.allowedTools()).isEmpty();
        assertThat(analysis.classification().flag("isFollowUp")).isTrue();
        assertThat(analysis.classification().entity("reference")).isEqualTo("PREVIOUS_ASSISTANT_RESPONSE");
    }

    @Test
    void checkAgainAfterWebSearchRunsWebAgain() {
        com.kesh.nlp.capability.ConversationState state = new com.kesh.nlp.capability.ConversationState();
        state.recordTurn(
                "latest Java",
                List.of("Java"),
                "current",
                "WEB_SEARCH",
                "WEB_SEARCH",
                "web_search",
                "r1",
                "CURRENT_KNOWLEDGE",
                true,
                "What is the latest Java version?",
                List.of()
        );
        RequestAnalysis analysis = analyzer.analyze(
                null,
                "Check again.",
                catalog,
                DeskContext.of(null, "What is the latest Java version?", "Java 25 is current.", state)
        );
        assertThat(analysis.allowedTools()).contains("web_search");
        assertThat(analysis.classification().flag("freshnessOverride")).isTrue();
    }
}
