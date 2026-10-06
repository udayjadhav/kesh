package com.kesh.learning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.kesh.agent.AgentResponse;
import com.kesh.config.KeshProperties;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.tools.ToolResult;
import com.kesh.workspace.WorkspaceMode;

/**
 * Slow path only. Never grants permission. Never blocks the user reply.
 */
@Component
public class AsyncInteractionRecorder {

    private static final Logger log = LoggerFactory.getLogger(AsyncInteractionRecorder.class);

    private final LearningEventStore store;
    private final KeshProperties properties;

    public AsyncInteractionRecorder(LearningEventStore store, KeshProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Async("keshTaskExecutor")
    public void record(
            WorkspaceMode mode,
            String userMessage,
            RequestAnalysis analysis,
            AgentResponse response,
            long latencyMs
    ) {
        record(mode, userMessage, analysis, response, latencyMs, "", null);
    }

    @Async("keshTaskExecutor")
    public void record(
            WorkspaceMode mode,
            String userMessage,
            RequestAnalysis analysis,
            AgentResponse response,
            long latencyMs,
            String interactionId
    ) {
        record(mode, userMessage, analysis, response, latencyMs, interactionId, null);
    }

    @Async("keshTaskExecutor")
    public void record(
            WorkspaceMode mode,
            String userMessage,
            RequestAnalysis analysis,
            AgentResponse response,
            long latencyMs,
            String interactionId,
            com.kesh.llm.RoutingDecision routed
    ) {
        if (!properties.getLearning().isEnabled() || !properties.getLearning().isAsync()) {
            return;
        }
        try {
            store.appendInteraction(toEvent(mode, userMessage, analysis, response, latencyMs, interactionId, routed));
        } catch (RuntimeException e) {
            log.info("Async interaction record skipped: {}", e.getMessage());
        }
    }

    private InteractionEvent toEvent(
            WorkspaceMode mode,
            String userMessage,
            RequestAnalysis analysis,
            AgentResponse response,
            long latencyMs,
            String interactionId,
            com.kesh.llm.RoutingDecision routed
    ) {
        String desk = mode == null ? "work" : mode.path();
        List<String> tools = new ArrayList<>();
        if (response != null && response.toolResults() != null) {
            for (ToolResult result : response.toolResults()) {
                if (result != null && result.tool() != null) {
                    tools.add(result.tool());
                }
            }
        }
        String id = interactionId == null || interactionId.isBlank()
                ? UUID.randomUUID().toString()
                : interactionId;
        String model = properties.getLlm().getModel();
        if (routed != null && routed.model() != null && !routed.model().isBlank()) {
            model = routed.model();
        }
        String knowledge = "";
        boolean followUp = false;
        String reference = "";
        if (analysis != null && analysis.classification() != null) {
            Object req = analysis.classification().entity("knowledgeRequirement");
            knowledge = req == null ? "" : req.toString();
            followUp = analysis.classification().flag("isFollowUp");
            Object ref = analysis.classification().entity("reference");
            reference = ref == null ? "" : ref.toString();
        }
        String selected = tools.isEmpty() ? "" : tools.get(0);
        List<String> candidates = analysis == null ? List.of() : List.copyOf(analysis.allowedTools());
        return new InteractionEvent(
                id,
                response == null ? "" : response.conversationId(),
                desk,
                clip(userMessage, 400),
                properties.getNlp().getClassifier().getType(),
                analysis == null ? "" : analysis.intent().name(),
                analysis == null ? 0 : analysis.intentConfidence(),
                analysis == null || analysis.plan() == null ? List.of() : analysis.plan().toMaps(),
                List.copyOf(tools),
                model,
                latencyMs,
                clip(response == null ? "" : response.message(), 800),
                Instant.now().toString(),
                knowledge,
                followUp,
                reference,
                candidates,
                selected,
                ""
        );
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String stripped = text.replaceAll("\\s+", " ").strip();
        if (stripped.length() <= max) {
            return stripped;
        }
        return stripped.substring(0, max) + "…";
    }
}
