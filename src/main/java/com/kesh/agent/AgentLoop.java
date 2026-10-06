package com.kesh.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.eval.AnswerJudge;
import com.kesh.eval.GroundingPolicy;
import com.kesh.llm.ChatTranscriptNormalizer;
import com.kesh.llm.LlmClient;
import com.kesh.llm.LlmRequest;
import com.kesh.llm.LlmResponse;
import com.kesh.llm.StreamConsumer;
import com.kesh.llm.ToolCallParser;
import com.kesh.nlp.Intent;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.RequestAnalyzer;
import com.kesh.nlp.Risk;
import com.kesh.observability.AgentAuditLogger;
import com.kesh.observability.AuditEvent;
import com.kesh.security.ApprovalManager;
import com.kesh.security.PendingApproval;
import com.kesh.security.PermissionDecision;
import com.kesh.security.ToolPermissionPolicy;
import com.kesh.routing.ToolCompatibilityValidator;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolCall;
import com.kesh.tools.ToolFailureKind;
import com.kesh.tools.ToolRegistry;
import com.kesh.tools.ToolResult;

@Component
public class AgentLoop {

    private static final Pattern WEB_CAPABILITY_REFUSE = Pattern.compile(
            "(?i)\\b(i (?:do not|don't|cannot|can't)\\b.{0,80}\\b(ability|access|weather|internet|external services|weather apis?|real-time|stock prices?)\\b)"
                    + "|(?i)\\bno (?:ability|access) to (?:access )?(?:external|the (?:web|internet)|weather)"
                    + "|(?i)cannot directly provide (?:real-time )?(?:stock )?prices?"
    );

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolPermissionPolicy permissionPolicy;
    private final ApprovalManager approvalManager;
    private final ToolCallParser parser;
    private final AgentAuditLogger auditLogger;
    private final KeshProperties properties;
    private final AnswerJudge answerJudge;
    private final com.kesh.llm.ModelRouter modelRouter;

    public AgentLoop(
            LlmClient llmClient,
            ToolRegistry toolRegistry,
            ToolPermissionPolicy permissionPolicy,
            ApprovalManager approvalManager,
            ToolCallParser parser,
            AgentAuditLogger auditLogger,
            KeshProperties properties
    ) {
        this(llmClient, toolRegistry, permissionPolicy, approvalManager, parser, auditLogger, properties, AnswerJudge.alwaysOk());
    }

    public AgentLoop(
            LlmClient llmClient,
            ToolRegistry toolRegistry,
            ToolPermissionPolicy permissionPolicy,
            ApprovalManager approvalManager,
            ToolCallParser parser,
            AgentAuditLogger auditLogger,
            KeshProperties properties,
            AnswerJudge answerJudge
    ) {
        this(llmClient, toolRegistry, permissionPolicy, approvalManager, parser, auditLogger, properties, answerJudge, new com.kesh.llm.ModelRouter(properties));
    }

    @Autowired
    public AgentLoop(
            LlmClient llmClient,
            ToolRegistry toolRegistry,
            ToolPermissionPolicy permissionPolicy,
            ApprovalManager approvalManager,
            ToolCallParser parser,
            AgentAuditLogger auditLogger,
            KeshProperties properties,
            AnswerJudge answerJudge,
            com.kesh.llm.ModelRouter modelRouter
    ) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.permissionPolicy = permissionPolicy;
        this.approvalManager = approvalManager;
        this.parser = parser;
        this.auditLogger = auditLogger;
        this.properties = properties;
        this.answerJudge = answerJudge == null ? AnswerJudge.alwaysOk() : answerJudge;
        this.modelRouter = modelRouter == null ? new com.kesh.llm.ModelRouter(properties) : modelRouter;
    }

    public AgentResponse run(ConversationContext context, AgentEventListener listener) {
        return run(context, listener, null);
    }

    public AgentResponse run(ConversationContext context, AgentEventListener listener, RequestAnalysis analysis) {
        AgentEventListener events = listener == null ? AgentEventListener.noop() : listener;
        if (analysis != null) {
            events.onEvent(AgentEvent.nlp(analysis));
            if (analysis.plan() != null) {
                events.onEvent(AgentEvent.routing(analysis.plan()));
            }
            if (analysis.skipModel()) {
                String skip = analysis.skipMessage() == null
                        ? "This request was stopped by the local classifier."
                        : analysis.skipMessage();
                context.addAssistant(skip);
                events.onEvent(AgentEvent.done(context.id(), skip));
                return AgentResponse.completed(context.id(), skip, List.of());
            }
            context.addClassifierHint(analysis.shortHint());
        }
        List<ToolResult> executed = new ArrayList<>();
        Set<String> noRetryTools = new LinkedHashSet<>();
        int blockedRounds = 0;
        prefetchPlan(context, analysis, executed, events);
        RequestAnalysis modelAnalysis = analysis;
        if (readyForGemmaRefine(analysis, executed)) {
            modelAnalysis = answerOnly(analysis);
            context.addClassifierHint(modelAnalysis.shortHint());
            context.addModelNudge(
                    "Search already ran. Write a short answer from the notes. "
                            + "Do not call tools. Do not write a Sources list."
            );
        }
        boolean webRefuseNudged = false;
        int judgeRetries = 0;
        int maxJudge = properties.getNlp().getClassifier().getJudgeMaxRetries();
        int max = Math.max(1, properties.getAgent().getMaxIterations());
        for (int iteration = 1; iteration <= max; iteration++) {
            events.onEvent(AgentEvent.thinking(iteration));
            LlmResponse response = completeModelTurn(context, modelAnalysis, events);
            if (response.failed()) {
                String error = response.error() == null ? "The language model failed." : response.error();
                events.onEvent(AgentEvent.error(error));
                audit(context, null, null, "ERROR", 0, null, error);
                return AgentResponse.error(context.id(), error);
            }
            List<ToolCall> toolCalls = response.toolCalls();
            if (toolCalls.isEmpty() && (parser.looksLikeInvalidToolProse(response.content())
                    || parser.looksLikeToolCall(response.content()))) {
                context.addModelNudge(invalidToolNudge(modelAnalysis));
                continue;
            }
            if (toolCalls.isEmpty()) {
                String text = parser.stripToolMarkup(response.content()).isBlank()
                        ? response.content()
                        : parser.stripToolMarkup(response.content());
                text = stripToolLeak(text);
                text = stripSourceDump(text);
                text = stripInternalProtocol(text);
                if (text.isBlank()) {
                    if (judgeRetries < maxJudge) {
                        judgeRetries++;
                        context.addModelNudge(
                                "Write a short plain-language answer from the tool results. "
                                        + "Do not paste TOOL_RESULT, JSON, or tool instructions."
                        );
                        continue;
                    }
                    text = "I found results but could not write a clean answer. Please ask again in a new chat.";
                }
                if (analysis != null
                        && analysis.allowedTools().contains("web_search")
                        && !alreadyRan(executed, "web_search")
                        && (WEB_CAPABILITY_REFUSE.matcher(text).find()
                        || GroundingPolicy.requiresVerification(analysis))) {
                    prefetchWebOnce(context, analysis, executed, events, searchQuery(context, analysis));
                    context.addModelNudge(
                            "Public web lookup is allowed. Answer from the search results. "
                                    + "Do not invent a specific count, price, or date. "
                                    + "Do not refuse stock prices, weather, or other live public facts."
                    );
                    continue;
                }
                if (!webRefuseNudged && looksLikeWebCapabilityRefuse(text, executed)) {
                    webRefuseNudged = true;
                    context.addAssistant(text);
                    context.addModelNudge(
                            "web_search already ran and returned sources. Answer from those results. "
                                    + "Never say you cannot access weather APIs, the internet, or external services."
                    );
                    continue;
                }
                AnswerJudge.JudgeVerdict verdict = answerJudge.evaluate(
                        context.lastUserMessage(), text, analysis, executed
                );
                if (!verdict.ok() && judgeRetries < maxJudge) {
                    judgeRetries++;
                    if (!text.isBlank()) {
                        context.addModelNudge("Draft answer (do not show this style to the user): " + text);
                    }
                    prefetchRetry(context, analysis, executed, events, verdict.retryIntents());
                    context.addModelNudge(
                            "Answer the latest user question in short plain language. "
                                    + "Do not apologize. Do not paste TOOL_RESULT, JSON, or tool instructions."
                    );
                    continue;
                }
                text = GroundingPolicy.enforce(text, analysis, executed);
                text = stripInternalProtocol(text);
                if (text.isBlank()) {
                    text = salvageText(executed, analysis);
                }
                context.addAssistant(text);
                events.onEvent(AgentEvent.done(context.id(), text, sourceMaps(executed)));
                return AgentResponse.completed(context.id(), text, executed);
            }
            if (noneMayRun(context, toolCalls, modelAnalysis, noRetryTools)) {
                for (ToolCall call : toolCalls) {
                    if (call != null && call.tool() != null) {
                        noRetryTools.add(call.tool());
                    }
                }
                blockedRounds++;
                if (blockedRounds >= 2) {
                    AgentResponse salvaged = salvageFromTools(context, analysis, executed, events);
                    if (salvaged != null) {
                        return salvaged;
                    }
                    String fallback = salvageText(executed, analysis);
                    fallback = GroundingPolicy.enforce(fallback, analysis, executed);
                    context.addAssistant(fallback);
                    events.onEvent(AgentEvent.done(context.id(), fallback, sourceMaps(executed)));
                    return AgentResponse.completed(context.id(), fallback, executed);
                }
                context.addModelNudge(refuseNudge(context, toolCalls, modelAnalysis));
                continue;
            }
            if (toolsAlreadySatisfied(analysis, executed)) {
                context.addModelNudge(
                        "You already have tool results. Answer the user in plain language now. Do not call tools."
                );
                continue;
            }
            context.addAssistantToolCalls(response.content(), toolCalls);
            for (ToolCall call : toolCalls) {
                AgentResponse maybePause = executeCall(
                        context, call, executed, events, false, modelAnalysis, noRetryTools
                );
                if (maybePause != null) {
                    return maybePause;
                }
            }
        }
        AgentResponse salvaged = salvageFromTools(context, analysis, executed, events);
        if (salvaged != null) {
            return salvaged;
        }
        AgentResponse maxed = AgentResponse.maxIterations(context.id());
        events.onEvent(AgentEvent.done(context.id(), maxed.message(), sourceMaps(executed)));
        return maxed;
    }

    public AgentResponse resumeApproved(ConversationContext context, PendingApproval approval, AgentEventListener listener) {
        List<ToolResult> executed = new ArrayList<>();
        AgentResponse pauseOrNull = executeCall(context, approval.toToolCall(), executed, listener, true, null, new LinkedHashSet<>());
        if (pauseOrNull != null) {
            return pauseOrNull;
        }
        return run(context, listener);
    }

    public AgentResponse resumeRejected(ConversationContext context, PendingApproval approval, AgentEventListener listener) {
        ToolResult rejected = ToolResult.fail(
                approval.tool(),
                "User rejected this action. Do not retry it unless the user asks again.",
                0
        );
        context.addToolResult(rejected);
        listener.onEvent(AgentEvent.toolResult(rejected));
        audit(context, approval.tool(), approval.arguments(), "REJECTED", 0, rejected, null);
        return run(context, listener);
    }

    private void prefetchPlan(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events
    ) {
        if (analysis == null) {
            return;
        }
        if (analysis.plan() != null && !analysis.plan().steps().isEmpty()) {
            for (com.kesh.routing.RoutingStep step : analysis.plan().steps()) {
                prefetchStep(context, analysis, executed, events, step);
            }
            return;
        }
        prefetchRuntimeCheck(context, analysis, executed, events);
        prefetchWebSearch(context, analysis, executed, events);
    }

    private void prefetchStep(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events,
            com.kesh.routing.RoutingStep step
    ) {
        if (step == null || step.intent() == null) {
            return;
        }
        switch (step.intent()) {
            case WEB_SEARCH, WEB_RESEARCH -> prefetchWebOnce(context, analysis, executed, events, step.query());
            case WEB_FETCH, WEB_CRAWL -> prefetchFetchOrSearch(context, analysis, executed, events, step);
            case RUNTIME_INSPECT -> prefetchInspectOnce(context, analysis, executed, events, step.target());
            default -> {
            }
        }
    }

    private void prefetchRuntimeCheck(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events
    ) {
        if (analysis == null || !analysis.allowedTools().contains("inspect_runtime")) {
            return;
        }
        String name = planTarget(analysis, com.kesh.nlp.capability.CapabilityIntent.RUNTIME_INSPECT);
        if (name == null || name.isBlank()) {
            name = RequestAnalyzer.inferRuntimeName(context.lastUserMessage());
        }
        if (name == null || name.isBlank()) {
            Object runtime = analysis.classification() == null ? null : analysis.classification().entities().get("runtime");
            name = runtime == null ? null : runtime.toString();
        }
        boolean inspectTurn = analysis.intent() == Intent.INSPECT
                || (analysis.classification() != null
                && analysis.classification().intent() == com.kesh.nlp.capability.CapabilityIntent.RUNTIME_INSPECT)
                || RequestAnalyzer.isRuntimeQuestion(context.lastUserMessage());
        if (!inspectTurn || name == null || name.isBlank()) {
            return;
        }
        prefetchInspectOnce(context, analysis, executed, events, name);
    }

    private static String planTarget(
            RequestAnalysis analysis,
            com.kesh.nlp.capability.CapabilityIntent intent
    ) {
        if (analysis == null || analysis.plan() == null || analysis.plan().steps() == null) {
            return null;
        }
        for (com.kesh.routing.RoutingStep step : analysis.plan().steps()) {
            if (step.intent() == intent && step.target() != null && !step.target().isBlank()) {
                return step.target();
            }
        }
        return null;
    }

    private void prefetchWebSearch(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events
    ) {
        if (analysis == null || !analysis.allowedTools().contains("web_search")) {
            return;
        }
        if (analysis.intent() != Intent.WEB_SEARCH
                && analysis.intent() != Intent.WEB_RESEARCH
                && analysis.intent() != Intent.WEB_FETCH) {
            return;
        }
        prefetchWebOnce(context, analysis, executed, events, searchQuery(context, analysis));
    }

    private void prefetchInspectOnce(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events,
            String name
    ) {
        if (analysis == null || !analysis.allowedTools().contains("inspect_runtime")) {
            return;
        }
        if (alreadyRan(executed, "inspect_runtime")) {
            return;
        }
        String runtime = name;
        if (runtime == null || runtime.isBlank()) {
            runtime = RequestAnalyzer.inferRuntimeName(context.lastUserMessage());
        }
        if (runtime == null || runtime.isBlank()) {
            return;
        }
        executeCall(context, ToolCall.of("inspect_runtime", java.util.Map.of("name", runtime)), executed, events, false, analysis, new LinkedHashSet<>());
    }

    private void prefetchWebOnce(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events,
            String query
    ) {
        if (analysis == null || !analysis.allowedTools().contains("web_search")) {
            return;
        }
        if (alreadyRan(executed, "web_search")) {
            return;
        }
        String q = query == null || query.isBlank() ? searchQuery(context, analysis) : query;
        if (q == null || q.isBlank()) {
            return;
        }
        executeCall(context, ToolCall.of("web_search", java.util.Map.of("query", q.strip())), executed, events, false, analysis, new LinkedHashSet<>());
    }

    private void prefetchFetchOrSearch(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events,
            com.kesh.routing.RoutingStep step
    ) {
        String query = step == null ? null : step.query();
        if (query != null && (query.startsWith("http://") || query.startsWith("https://"))) {
            return;
        }
        prefetchWebOnce(context, analysis, executed, events, query);
    }

    private static boolean readyForGemmaRefine(RequestAnalysis analysis, List<ToolResult> executed) {
        if (analysis == null || analysis.intent() == Intent.WEB_RESEARCH) {
            return false;
        }
        return alreadyRan(executed, "web_search") || alreadyRan(executed, "web_fetch");
    }

    private static boolean alreadyRan(List<ToolResult> executed, String tool) {
        return executed.stream().anyMatch(result -> tool.equals(result.tool()) && result.success());
    }

    private static boolean alreadySucceeded(List<ToolResult> executed, ToolCall call) {
        if (call == null || call.tool() == null) {
            return false;
        }
        if (!("web_search".equals(call.tool())
                || "inspect_runtime".equals(call.tool())
                || "web_fetch".equals(call.tool())
                || "web_crawl".equals(call.tool()))) {
            return false;
        }
        return alreadyRan(executed, call.tool());
    }

    private static boolean toolsAlreadySatisfied(RequestAnalysis analysis, List<ToolResult> executed) {
        if (alreadyRan(executed, "web_search") && analysis != null
                && (analysis.intent() == Intent.WEB_SEARCH || analysis.intent() == Intent.WEB_FETCH)) {
            return true;
        }
        long webHits = 0;
        for (ToolResult result : executed) {
            if (result != null && result.success() && isWebTool(result.tool())) {
                webHits++;
            }
        }
        return analysis != null && analysis.intent() == Intent.WEB_RESEARCH && webHits >= 3;
    }

    private static boolean isWebTool(String tool) {
        return "web_search".equals(tool) || "web_fetch".equals(tool) || "web_crawl".equals(tool);
    }

    private AgentResponse salvageFromTools(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events
    ) {
        if (!hasWebEvidence(executed)) {
            return null;
        }
        context.addModelNudge(
                "Stop calling tools. Write a short final answer from the tool results. Do not call tools."
        );
        LlmResponse last = completeModelTurn(context, answerOnly(analysis), events);
        if (last != null && !last.failed() && (last.toolCalls() == null || last.toolCalls().isEmpty())) {
            String text = stripInternalProtocol(stripSourceDump(stripToolLeak(parser.stripToolMarkup(last.content()))));
            if (text != null && !text.isBlank() && !text.contains("maximum number of tool steps")) {
                context.addAssistant(text);
                events.onEvent(AgentEvent.done(context.id(), text, sourceMaps(executed)));
                return AgentResponse.completed(context.id(), text, executed);
            }
        }
        String summary = sourceSummary(executed);
        context.addAssistant(summary);
        events.onEvent(AgentEvent.done(context.id(), summary, sourceMaps(executed)));
        return AgentResponse.completed(context.id(), summary, executed);
    }

    private static RequestAnalysis answerOnly(RequestAnalysis analysis) {
        if (analysis == null) {
            return new RequestAnalysis(
                    Intent.CHAT, 1.0, Risk.READ, 1.0, List.of(), true, java.util.Set.of(), false, null
            );
        }
        return new RequestAnalysis(
                analysis.intent(),
                analysis.intentConfidence(),
                analysis.risk(),
                analysis.riskConfidence(),
                analysis.entities(),
                true,
                java.util.Set.of(),
                false,
                null,
                analysis.classification(),
                analysis.plan()
        );
    }

    private static boolean hasWebEvidence(List<ToolResult> executed) {
        if (alreadyRan(executed, "web_search") || alreadyRan(executed, "web_fetch") || alreadyRan(executed, "web_crawl")) {
            return true;
        }
        return !sourceMaps(executed).isEmpty();
    }

    private static String sourceSummary(List<ToolResult> executed) {
        java.util.List<java.util.Map<String, Object>> sources = sourceMaps(executed);
        if (sources.isEmpty()) {
            for (int i = executed.size() - 1; i >= 0; i--) {
                ToolResult result = executed.get(i);
                if (result != null && result.success() && result.output() != null && !result.output().isBlank()) {
                    String output = result.output().strip();
                    if (output.length() > 1200) {
                        output = output.substring(0, 1200) + "…";
                    }
                    return output;
                }
            }
            return "I found results but could not finish a clean summary. Ask again in a new chat.";
        }
        StringBuilder sb = new StringBuilder("Here is what I found:\n");
        int n = 0;
        for (java.util.Map<String, Object> source : sources) {
            n++;
            Object title = source.get("title") == null ? source.get("source") : source.get("title");
            Object url = source.get("url");
            sb.append(n).append(". ").append(title == null ? "Source" : title);
            if (url != null && !url.toString().isBlank()) {
                sb.append(" — ").append(url);
            }
            sb.append('\n');
            if (n >= 8) {
                break;
            }
        }
        return sb.toString().strip();
    }

    private void prefetchRetry(
            ConversationContext context,
            RequestAnalysis analysis,
            List<ToolResult> executed,
            AgentEventListener events,
            java.util.List<com.kesh.nlp.capability.CapabilityIntent> retryIntents
    ) {
        if (retryIntents == null || retryIntents.isEmpty()) {
            return;
        }
        for (com.kesh.nlp.capability.CapabilityIntent intent : retryIntents) {
            if (intent == com.kesh.nlp.capability.CapabilityIntent.WEB_SEARCH
                    || intent == com.kesh.nlp.capability.CapabilityIntent.WEB_RESEARCH) {
                prefetchWebOnce(context, analysis, executed, events, searchQuery(context, analysis));
            } else if (intent == com.kesh.nlp.capability.CapabilityIntent.RUNTIME_INSPECT) {
                prefetchInspectOnce(context, analysis, executed, events, null);
            }
        }
    }

    private static String searchQuery(ConversationContext context, RequestAnalysis analysis) {
        String current = context == null ? "" : context.lastUserMessage();
        boolean followUp = analysis != null && analysis.classification() != null
                && analysis.classification().flag("isFollowUp");
        if (!followUp && current != null && !current.isBlank()) {
            return current.strip();
        }
        if (analysis != null && analysis.plan() != null) {
            for (com.kesh.routing.RoutingStep step : analysis.plan().steps()) {
                if (step.query() != null && !step.query().isBlank()
                        && (step.intent() == com.kesh.nlp.capability.CapabilityIntent.WEB_SEARCH
                        || step.intent() == com.kesh.nlp.capability.CapabilityIntent.WEB_RESEARCH)) {
                    return step.query();
                }
            }
        }
        if (analysis != null && analysis.classification() != null) {
            Object query = analysis.classification().entities().get("query");
            if (query != null && !query.toString().isBlank()) {
                return query.toString();
            }
        }
        return current;
    }

    private static boolean isCompleteAsk(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return com.kesh.nlp.capability.OpenNlpCapabilityClassifier.tokens(text).length >= 3;
    }

    private static boolean looksLikeWebCapabilityRefuse(
            String text,
            List<ToolResult> executed
    ) {
        if (text == null || !alreadyRan(executed, "web_search")) {
            return false;
        }
        return WEB_CAPABILITY_REFUSE.matcher(text).find();
    }

    private AgentResponse executeCall(
            ConversationContext context,
            ToolCall call,
            List<ToolResult> executed,
            AgentEventListener listener,
            boolean explicitlyApproved,
            RequestAnalysis analysis,
            Set<String> noRetryTools
    ) {
        Set<String> blocked = noRetryTools == null ? new LinkedHashSet<>() : noRetryTools;
        if (call != null && call.tool() != null && blocked.contains(call.tool())) {
            context.addModelNudge("Do not retry that tool. Answer in plain language or use an allowed tool.");
            return null;
        }
        ToolCompatibilityValidator.Result compatibility = ToolCompatibilityValidator.validate(
                call, analysis, context.mode()
        );
        if (!compatibility.compatible()) {
            blocked.add(call.tool());
            context.addModelNudge(
                    "That tool is not available this turn. "
                            + (analysis != null && analysis.allowedTools() != null && !analysis.allowedTools().isEmpty()
                            ? "You may only use: " + String.join(", ", analysis.allowedTools()) + "."
                            : "Answer in plain language. Do not call tools.")
            );
            return null;
        }
        if (!mayRun(context, call, analysis)) {
            ToolResult denied = ToolResult.fail(call.tool(), refuseReason(context, call, analysis), 0);
            context.addToolResult(denied);
            executed.add(denied);
            listener.onEvent(AgentEvent.toolResult(denied));
            audit(context, call.tool(), call.arguments(), "DENY", 0, denied, denied.error());
            return null;
        }
        Tool tool = toolRegistry.get(call.tool()).orElse(null);
        if (tool == null) {
            ToolResult unknown = ToolResult.fail(call.tool(), "Unknown tool: " + call.tool(), 0);
            context.addToolResult(unknown);
            executed.add(unknown);
            listener.onEvent(AgentEvent.toolResult(unknown));
            audit(context, call.tool(), call.arguments(), "DENY", 0, unknown, unknown.error());
            return null;
        }
        PermissionDecision decision = permissionPolicy.evaluateApproved(tool, explicitlyApproved);
        if (decision == PermissionDecision.DENY) {
            blocked.add(tool.name());
            ToolResult denied = ToolResult.fail(tool.name(), "Tool is not permitted by policy.", 0);
            context.addToolResult(denied);
            executed.add(denied);
            listener.onEvent(AgentEvent.toolResult(denied));
            audit(context, tool.name(), call.arguments(), "DENY", denied.durationMs(), denied, denied.error());
            return null;
        }
        if (decision == PermissionDecision.APPROVAL_REQUIRED) {
            PendingApproval pending = approvalManager.create(context.mode(), context.id(), call, tool.permissionLevel());
            listener.onEvent(AgentEvent.approvalRequired(pending));
            audit(context, tool.name(), call.arguments(), "PENDING", 0, null, null);
            return AgentResponse.approval(context.id(), pending);
        }
        if (alreadySucceeded(executed, call)) {
            context.addModelNudge("That tool already succeeded. Answer from the existing result. Do not call it again.");
            return null;
        }
        listener.onEvent(AgentEvent.toolStart(call));
        ToolResult result;
        try {
            result = tool.execute(call.arguments());
        } catch (RuntimeException e) {
            result = ToolResult.fail(tool.name(), "Tool crashed: " + e.getMessage(), 0);
        }
        if (!result.success()) {
            ToolFailureKind kind = ToolFailureKind.classify(result.error());
            if (!kind.mayRetrySameTool()) {
                blocked.add(tool.name());
            }
        }
        ToolResult forModel = result;
        if (result.success() && isWebTool(result.tool())) {
            forModel = new ToolResult(
                    result.tool(),
                    true,
                    compactSearchNotes(result),
                    result.error(),
                    result.durationMs(),
                    result.sources()
            );
        } else if (result.success()) {
            forModel = new ToolResult(
                    result.tool(),
                    true,
                    result.output() + "\n\nIf this answers the user, reply now in plain language. Do not repeat this tool.",
                    result.error(),
                    result.durationMs(),
                    result.sources()
            );
        }
        context.addToolResult(forModel);
        executed.add(result);
        listener.onEvent(AgentEvent.toolResult(result));
        audit(
                context,
                tool.name(),
                call.arguments(),
                "APPROVED",
                result.durationMs(),
                result,
                result.success() ? null : result.error()
        );
        return null;
    }

    private LlmResponse completeModelTurn(
            ConversationContext context,
            RequestAnalysis analysis,
            AgentEventListener listener
    ) {
        AtomicReference<LlmResponse> completed = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean decided = new AtomicBoolean(false);
        AtomicBoolean streamToUser = new AtomicBoolean(false);
        StringBuilder buffer = new StringBuilder();
        java.util.Set<String> toolFilter = analysis != null && analysis.restrictTools()
                ? analysis.allowedTools()
                : null;
        boolean independent = analysis == null || analysis.classification() == null
                || !analysis.classification().flag("isFollowUp");
        LlmRequest request = new LlmRequest(
                modelRouter.decide(analysis, context.lastUserMessage()).model(),
                ChatTranscriptNormalizer.forChatModel(context.messages(), toolFilter, independent),
                true
        );
        llmClient.chatStreaming(request, new StreamConsumer() {
            @Override
            public void onToken(String token) {
                buffer.append(token);
                if (!decided.get()) {
                    String peek = buffer.toString().stripLeading();
                    if (parser.looksLikeToolCall(peek)
                            || parser.looksLikeToolTagStart(peek)
                            || peek.contains("TOOL_RESULT")) {
                        decided.set(true);
                        streamToUser.set(false);
                        return;
                    }
                    if (peek.startsWith("<") && peek.length() < 16) {
                        return;
                    }
                    if (peek.startsWith("{") && peek.length() < 24 && peek.contains("\"tool")) {
                        return;
                    }
                    if (peek.length() >= 24) {
                        decided.set(true);
                        streamToUser.set(true);
                        listener.onEvent(AgentEvent.token(buffer.toString()));
                    }
                    return;
                }
                if (streamToUser.get()) {
                    listener.onEvent(AgentEvent.token(token));
                }
            }

            @Override
            public void onComplete(LlmResponse response) {
                if (!decided.get() && !parser.looksLikeToolCall(buffer.toString()) && !buffer.isEmpty()) {
                    listener.onEvent(AgentEvent.token(buffer.toString()));
                }
                completed.set(response);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }
        });
        if (error.get() != null) {
            return LlmResponse.failure(error.get().getMessage());
        }
        if (completed.get() == null) {
            return LlmResponse.failure("The language model returned no response.");
        }
        return completed.get();
    }

    private static boolean noneMayRun(
            ConversationContext context,
            List<ToolCall> calls,
            RequestAnalysis analysis,
            Set<String> noRetryTools
    ) {
        for (ToolCall call : calls) {
            if (call != null && noRetryTools != null && noRetryTools.contains(call.tool())) {
                continue;
            }
            if (mayRun(context, call, analysis)
                    && ToolCompatibilityValidator.validate(call, analysis, context.mode()).compatible()) {
                return false;
            }
        }
        return true;
    }

    private static boolean mayRun(ConversationContext context, ToolCall call, RequestAnalysis analysis) {
        if (call == null || call.tool() == null) {
            return false;
        }
        if (!context.mode().allowsTool(call.tool())) {
            return false;
        }
        if ("current_time".equals(call.tool()) && !RequestAnalyzer.isClockQuestion(context.lastUserMessage())) {
            return false;
        }
        if (analysis != null && analysis.restrictTools() && !analysis.allowedTools().contains(call.tool())) {
            return false;
        }
        return true;
    }

    private static String refuseNudge(ConversationContext context, List<ToolCall> calls, RequestAnalysis analysis) {
        if (analysis != null && analysis.allowedTools().contains("inspect_runtime")) {
            return "The user asked what is installed on this Mac. Quote inspect_runtime if it already ran. "
                    + "If it has not run, call inspect_runtime. Never say you cannot access this Mac. Do not call current_time.";
        }
        if (analysis != null && (analysis.allowedTools().contains("web_search")
                || analysis.allowedTools().contains("web_fetch")
                || analysis.allowedTools().contains("web_crawl"))) {
            return "Public web tools are allowed this turn. Call web_search, web_fetch, or web_crawl. "
                    + "Do not invent URLs or sources. Do not call current_time. "
                    + "Never say you cannot access weather APIs, the internet, or external services.";
        }
        boolean clockOnly = true;
        for (ToolCall call : calls) {
            if (!"current_time".equals(call.tool())) {
                clockOnly = false;
                break;
            }
        }
        if (clockOnly && !RequestAnalyzer.isClockQuestion(context.lastUserMessage())) {
            return "The user did not ask for the current time. Answer in plain language. Do not call current_time.";
        }
        return "Do not call tools this turn. Answer the user in plain language. Do not call current_time.";
    }

    private static String refuseReason(ConversationContext context, ToolCall call, RequestAnalysis analysis) {
        if ("current_time".equals(call.tool()) && !RequestAnalyzer.isClockQuestion(context.lastUserMessage())) {
            return "The user did not ask for the current time.";
        }
        if (!context.mode().allowsTool(call.tool())) {
            return "This tool is not available on the " + context.mode().label() + " desk.";
        }
        if (analysis != null && analysis.restrictTools()) {
            String allowed = analysis.allowedTools().isEmpty()
                    ? "none"
                    : String.join(", ", analysis.allowedTools());
            return "The request classifier blocked this tool for intent " + analysis.intent()
                    + ". Allowed tools this turn: " + allowed + ".";
        }
        return "This tool is not allowed for this request.";
    }

    private static java.util.List<java.util.Map<String, Object>> sourceMaps(java.util.List<ToolResult> executed) {
        java.util.LinkedHashMap<String, java.util.Map<String, Object>> unique = new java.util.LinkedHashMap<>();
        for (ToolResult result : executed) {
            if (result == null || result.sources() == null) {
                continue;
            }
            for (java.util.Map<String, Object> source : result.sources()) {
                Object url = source.get("url");
                String key = url == null ? String.valueOf(source) : url.toString();
                unique.putIfAbsent(key, source);
            }
        }
        return java.util.List.copyOf(unique.values());
    }

    private static String compactSearchNotes(ToolResult result) {
        if (result != null && result.sources() != null && !result.sources().isEmpty()) {
            StringBuilder sb = new StringBuilder("Search notes (answer from this; do not list sources):\n");
            int n = 0;
            for (java.util.Map<String, Object> source : result.sources()) {
                n++;
                if (n > 5) {
                    break;
                }
                Object title = source.get("title") == null ? source.get("source") : source.get("title");
                Object snippet = source.get("snippet");
                sb.append(n).append(". ").append(title == null ? "Result" : title).append('\n');
                if (snippet != null && !snippet.toString().isBlank()) {
                    sb.append(clip(snippet.toString(), 280)).append('\n');
                }
            }
            return sb.toString().strip();
        }
        String output = result == null || result.output() == null ? "" : result.output();
        int json = output.indexOf("\nJSON:");
        if (json >= 0) {
            output = output.substring(0, json).strip();
        }
        return clip(output, 1800);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String stripped = text.strip();
        if (stripped.length() <= max) {
            return stripped;
        }
        return stripped.substring(0, max) + "…";
    }

    private static String stripToolLeak(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text;
        int index = cleaned.indexOf("TOOL_RESULT");
        if (index >= 0) {
            cleaned = cleaned.substring(0, index);
        }
        cleaned = FAKE_TOOL_XML.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("Do not invent extra URLs. Quote these sources if you use them.", "");
        cleaned = cleaned.replace("If this answers the user, reply now in plain language. Do not repeat this tool. Do not invent extra URLs.", "");
        cleaned = cleaned.replace("If this answers the user, reply now in plain language. Do not repeat this tool.", "");
        return cleaned.strip();
    }

    private static final Pattern FAKE_TOOL_XML = Pattern.compile(
            "(?is)<(web_search|web_fetch|web_crawl|web_research|search|tool_call)\\b[^>]*>.*?(</\\1>|$)"
    );
    private static final Pattern INTERNAL_PROTOCOL = Pattern.compile(
            "(?is)your last tool call was malformed[^\\n]*"
                    + "|respond with a valid <tool_call>[^\\n]*"
                    + "|never emit xml tags[^\\n]*"
                    + "|<tool_call>.*?</tool_call>"
    );

    private static String stripInternalProtocol(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = INTERNAL_PROTOCOL.matcher(text).replaceAll("");
        cleaned = cleaned.replace("[[kesh-internal]]", "");
        String lower = cleaned.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("your last tool call was malformed")
                || lower.contains("respond with a valid <tool_call>")) {
            return "";
        }
        return cleaned.strip();
    }

    private String salvageText(List<ToolResult> executed, RequestAnalysis analysis) {
        if (hasWebEvidence(executed)) {
            String summary = sourceSummary(executed);
            if (summary != null && !summary.isBlank()) {
                return summary;
            }
        }
        if (GroundingPolicy.requiresVerification(analysis)) {
            return GroundingPolicy.unverifiedCurrentAnswer();
        }
        return "I couldn't complete that with the tools allowed for this request. Try asking another way.";
    }

    private static String invalidToolNudge(RequestAnalysis analysis) {
        if (analysis != null && analysis.allowedTools() != null
                && (analysis.allowedTools().contains("web_search")
                || analysis.allowedTools().contains("web_fetch")
                || analysis.allowedTools().contains("web_crawl"))) {
            return "That was not a valid tool call. If you need a tool, emit only:\n<tool_call>\n{\"tool\":\"web_search\",\"arguments\":{\"query\":\"...\"}}\n</tool_call>\n"
                    + "Never write <web_search> tags or fake JSON results. If search notes are already in the chat, answer from them.";
        }
        return "Do not emit tool XML or JSON. Answer the user in plain language. Do not call tools this turn.";
    }

    private static String stripSourceDump(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text;
        int cut = -1;
        String[] markers = {
                "\nSource 1:",
                "\n**Source 1**",
                "\nSources:",
                "\n**Sources**",
                "\nSources\n"
        };
        for (String marker : markers) {
            int at = indexOfIgnoreCase(cleaned, marker);
            if (at >= 0 && (cut < 0 || at < cut)) {
                cut = at;
            }
        }
        if (cut >= 0) {
            cleaned = cleaned.substring(0, cut);
        }
        return cleaned.strip();
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase(java.util.Locale.ROOT).indexOf(needle.toLowerCase(java.util.Locale.ROOT));
    }

    private void audit(
            ConversationContext context,
            String tool,
            java.util.Map<String, Object> arguments,
            String approvalStatus,
            long duration,
            ToolResult result,
            String error
    ) {
        auditLogger.log(new AuditEvent(
                Instant.now(),
                context.id(),
                "",
                properties.getLlm().getModel(),
                tool,
                arguments,
                tool == null ? null : toolRegistry.get(tool).map(Tool::permissionLevel).orElse(null),
                approvalStatus,
                duration,
                result == null ? null : result.renderForModel(),
                error
        ));
    }
}
