package com.kesh.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.kesh.learning.AsyncInteractionRecorder;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.RequestAnalyzer;
import com.kesh.nlp.capability.DeskContext;
import com.kesh.observability.KeshMetrics;
import com.kesh.rag.ContextAssembler;
import com.kesh.rag.ConversationMemory;
import com.kesh.rag.KnowledgeCorpus;
import com.kesh.security.ApprovalManager;
import com.kesh.security.PendingApproval;
import com.kesh.tools.ToolRegistry;
import com.kesh.workspace.Workspace;
import com.kesh.workspace.WorkspaceMode;

@Service
public class AgentOrchestrator {

    private final ConversationStore conversationStore;
    private final AgentLoop agentLoop;
    private final ApprovalManager approvalManager;
    private final RequestAnalyzer requestAnalyzer;
    private final ToolRegistry toolRegistry;
    private final ConversationMemory conversationMemory;
    private final KnowledgeCorpus knowledgeCorpus;
    private final ContextAssembler contextAssembler;
    private final AsyncInteractionRecorder interactionRecorder;
    private final KeshMetrics metrics;
    private final com.kesh.eval.AsyncTrajectoryJudge trajectoryJudge;
    private final com.kesh.writing.WritingEngine writingEngine;

    public AgentOrchestrator(
            ConversationStore conversationStore,
            AgentLoop agentLoop,
            ApprovalManager approvalManager,
            RequestAnalyzer requestAnalyzer,
            ToolRegistry toolRegistry,
            ConversationMemory conversationMemory,
            KnowledgeCorpus knowledgeCorpus,
            ContextAssembler contextAssembler,
            AsyncInteractionRecorder interactionRecorder,
            KeshMetrics metrics,
            com.kesh.eval.AsyncTrajectoryJudge trajectoryJudge,
            com.kesh.writing.WritingEngine writingEngine
    ) {
        this.conversationStore = conversationStore;
        this.agentLoop = agentLoop;
        this.approvalManager = approvalManager;
        this.requestAnalyzer = requestAnalyzer;
        this.toolRegistry = toolRegistry;
        this.conversationMemory = conversationMemory;
        this.knowledgeCorpus = knowledgeCorpus;
        this.contextAssembler = contextAssembler;
        this.interactionRecorder = interactionRecorder;
        this.metrics = metrics;
        this.trajectoryJudge = trajectoryJudge;
        this.writingEngine = writingEngine;
    }

    public AgentResponse handle(String conversationId, String userMessage, AgentEventListener listener) {
        return handle(WorkspaceMode.PROFESSIONAL, conversationId, userMessage, listener);
    }

    public AgentResponse handle(
            WorkspaceMode mode,
            String conversationId,
            String userMessage,
            AgentEventListener listener
    ) {
        WorkspaceMode desk = mode == null ? WorkspaceMode.PROFESSIONAL : mode;
        Workspace.set(desk);
        try {
            ConversationContext context = conversationStore.getOrCreate(desk, conversationId);
            RequestAnalysis analysis = requestAnalyzer.analyze(
                    desk,
                    userMessage,
                    toolRegistry.forMode(desk),
                    DeskContext.of(
                            desk,
                            context.lastUserMessage(),
                            context.lastAssistantMessage(),
                            context.conversationState()
                    )
            );
            var related = new java.util.ArrayList<com.kesh.rag.RelatedSnippet>();
            if (useConversationMemory(analysis)) {
                related.addAll(conversationMemory.retrieve(desk, context.id(), userMessage));
            }
            if (useCompanyKnowledge(analysis)) {
                related.addAll(knowledgeCorpus.retrieve(userMessage));
            }
            var assembled = contextAssembler.assemble(
                    userMessage,
                    related,
                    List.of()
            );
            context.addSavedNotes(contextAssembler.promptBlock(assembled));
            AgentEventListener events = listener == null ? AgentEventListener.noop() : listener;
            events.onEvent(AgentEvent.nlp(analysis));
            if (analysis != null && analysis.plan() != null) {
                events.onEvent(AgentEvent.routing(analysis.plan()));
            }
            events.onEvent(AgentEvent.context(assembled));
            context.addUser(userMessage);
            long started = System.nanoTime();
            String interactionId = UUID.randomUUID().toString();
            AgentEventListener tagged = event -> {
                if (event != null && "done".equals(event.type())) {
                    Map<String, Object> data = new LinkedHashMap<>(event.data());
                    data.put("interactionId", interactionId);
                    events.onEvent(new AgentEvent("done", data));
                    return;
                }
                events.onEvent(event);
            };
            AgentResponse response;
        if (writingEngine != null
                    && writingEngine.enabled()
                    && com.kesh.writing.WritingEngine.isWritingOnly(analysis)) {
                response = writingEngine.handle(context, analysis, userMessage, tagged, interactionId)
                        .withNlp(analysis);
            } else {
                response = agentLoop
                        .run(context, tagged, analysis)
                        .withNlp(analysis);
            }
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            rememberTurn(context, analysis, response, interactionId, userMessage);
            persist(context);
            com.kesh.llm.RoutingDecision used = com.kesh.llm.RoutingContext.get();
            interactionRecorder.record(
                    desk,
                    userMessage,
                    analysis,
                    response,
                    latencyMs,
                    interactionId,
                    used
            );
            if (trajectoryJudge != null) {
                trajectoryJudge.evaluate(desk, userMessage, analysis, response, interactionId, used);
            }
            if (metrics != null) {
                metrics.recordTurn(analysis, latencyMs, analysis != null && analysis.skipModel());
            }
            return response;
        } finally {
            com.kesh.llm.RoutingContext.clear();
            Workspace.clear();
        }
    }

    public AgentResponse approve(String approvalId, AgentEventListener listener) {
        return approve(WorkspaceMode.PROFESSIONAL, approvalId, listener);
    }

    public AgentResponse approve(WorkspaceMode mode, String approvalId, AgentEventListener listener) {
        PendingApproval pending = approvalManager.getRequired(approvalId);
        requireSameDesk(mode, pending);
        Workspace.set(pending.mode());
        try {
            PendingApproval approval = approvalManager.approve(approvalId);
            ConversationContext context = conversationStore.require(approval.mode(), approval.conversationId());
            AgentResponse response = agentLoop.resumeApproved(
                    context,
                    approval,
                    listener == null ? AgentEventListener.noop() : listener
            );
            persist(context);
            return response;
        } finally {
            Workspace.clear();
        }
    }

    public AgentResponse reject(String approvalId, AgentEventListener listener) {
        return reject(WorkspaceMode.PROFESSIONAL, approvalId, listener);
    }

    public AgentResponse reject(WorkspaceMode mode, String approvalId, AgentEventListener listener) {
        PendingApproval pending = approvalManager.getRequired(approvalId);
        requireSameDesk(mode, pending);
        Workspace.set(pending.mode());
        try {
            PendingApproval approval = approvalManager.reject(approvalId);
            ConversationContext context = conversationStore.require(approval.mode(), approval.conversationId());
            AgentResponse response = agentLoop.resumeRejected(
                    context,
                    approval,
                    listener == null ? AgentEventListener.noop() : listener
            );
            persist(context);
            return response;
        } finally {
            Workspace.clear();
        }
    }

    private void persist(ConversationContext context) {
        conversationStore.save(context);
        conversationMemory.index(context.mode(), context.id(), context.title(), context.transcript());
    }

    private static void rememberTurn(
            ConversationContext context,
            RequestAnalysis analysis,
            AgentResponse response,
            String interactionId,
            String userMessage
    ) {
        if (context == null || context.conversationState() == null) {
            return;
        }
        String capability = analysis == null || analysis.classification() == null
                ? ""
                : analysis.classification().intent().name();
        String knowledge = analysis == null || analysis.classification() == null
                || analysis.classification().entity("knowledgeRequirement") == null
                ? ""
                : analysis.classification().entity("knowledgeRequirement").toString();
        String tool = "";
        boolean usedWeb = false;
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        if (response != null && response.toolResults() != null) {
            for (com.kesh.tools.ToolResult result : response.toolResults()) {
                if (result == null || result.tool() == null) {
                    continue;
                }
                if (tool.isBlank()) {
                    tool = result.tool();
                }
                if ("web_search".equals(result.tool()) || "web_fetch".equals(result.tool())
                        || "web_crawl".equals(result.tool())) {
                    usedWeb = true;
                }
                if (result.sources() != null) {
                    results.addAll(result.sources());
                }
            }
        }
        String query = userMessage;
        if (analysis != null && analysis.classification() != null
                && analysis.classification().entity("query") != null) {
            query = analysis.classification().entity("query").toString();
        }
        String timeContext = usedWeb || (knowledge.contains("CURRENT")) ? "current" : "";
        context.conversationState().recordTurn(
                clipTopic(userMessage),
                analysis == null ? java.util.List.of() : analysis.entities(),
                timeContext,
                analysis == null || analysis.intent() == null ? "" : analysis.intent().name(),
                capability,
                tool,
                interactionId,
                knowledge,
                usedWeb,
                query,
                results
        );
    }

    private static String clipTopic(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").strip();
        return compact.length() <= 80 ? compact : compact.substring(0, 80) + "…";
    }

    private static boolean useConversationMemory(RequestAnalysis analysis) {
        if (analysis == null || analysis.skipModel()) {
            return false;
        }
        if (analysis.classification() != null && !analysis.classification().flag("isFollowUp")) {
            return false;
        }
        if (analysis.intent() == com.kesh.nlp.Intent.WRITING) {
            return false;
        }
        if (analysis.intent() == com.kesh.nlp.Intent.WEB_SEARCH
                || analysis.intent() == com.kesh.nlp.Intent.WEB_FETCH
                || analysis.intent() == com.kesh.nlp.Intent.WEB_RESEARCH
                || analysis.intent() == com.kesh.nlp.Intent.APPS
                || analysis.intent() == com.kesh.nlp.Intent.WORK) {
            return false;
        }
        return true;
    }

    private static boolean useCompanyKnowledge(RequestAnalysis analysis) {
        if (analysis == null || analysis.skipModel()) {
            return false;
        }
        if (analysis.classification() != null) {
            Object knowledge = analysis.classification().entity("knowledgeRequirement");
            if (knowledge != null && "COMPANY_KNOWLEDGE".equals(knowledge.toString())) {
                return true;
            }
            return analysis.classification().domain() == com.kesh.nlp.capability.CapabilityDomain.RAG;
        }
        return false;
    }

    private static boolean useLocalKnowledge(RequestAnalysis analysis) {
        return useConversationMemory(analysis) || useCompanyKnowledge(analysis);
    }

    private static void requireSameDesk(WorkspaceMode mode, PendingApproval approval) {
        WorkspaceMode desk = mode == null ? WorkspaceMode.PROFESSIONAL : mode;
        if (approval.mode() != desk) {
            throw new IllegalArgumentException("Unknown approval request: " + approval.id());
        }
    }
}
