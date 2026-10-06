package com.kesh.eval;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.agent.AgentResponse;
import com.kesh.config.KeshProperties;
import com.kesh.learning.EvaluationEvent;
import com.kesh.learning.LearningEventStore;
import com.kesh.llm.LlmClient;
import com.kesh.llm.LlmMessage;
import com.kesh.llm.LlmRequest;
import com.kesh.llm.LlmResponse;
import com.kesh.llm.ModelRouter;
import com.kesh.llm.RoutingDecision;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.capability.LlmIntentClassifierFallback;
import com.kesh.workspace.WorkspaceMode;

/**
 * Slow-path local judge. Does not block the user reply. Does not authorize tools.
 */
@Component
public class AsyncTrajectoryJudge {

    private static final Logger log = LoggerFactory.getLogger(AsyncTrajectoryJudge.class);
    private static final String SYSTEM = """
            Grade this Kesh turn. Return ONLY JSON:
            {"groundedness":0.0,"correctness":0.0,"completeness":0.0,"instruction_following":0.0,"hallucination":0.0,"tool_use":0.0,"notes":""}
            Scores are 0 to 1. hallucination=1 means invented facts. tool_use=1 means tools were used well.
            Penalize showing <web_search> XML to the user, inventing search results, or searching when local knowledge was enough.
            Penalize summarizing a previous answer by searching again.
            Do not suggest commands, files, or policy changes.
            """;
    private static final String WRITING_SYSTEM = """
            Grade this Kesh writing turn. Return ONLY JSON:
            {"instruction_following":0.0,"semantic_preservation":0.0,"factual_preservation":0.0,"tone_adherence":0.0,"naturalness":0.0,"verbosity":0.0,"invented_information":0.0,"notes":""}
            Scores are 0 to 1. invented_information=1 means the draft added facts. verbosity=1 means it got longer without need.
            Do not rewrite the user's message. Do not send anything.
            """;

    private final LlmClient llmClient;
    private final ModelRouter modelRouter;
    private final LearningEventStore store;
    private final KeshProperties properties;
    private final ObjectMapper objectMapper;

    public AsyncTrajectoryJudge(
            LlmClient llmClient,
            ModelRouter modelRouter,
            LearningEventStore store,
            KeshProperties properties,
            ObjectMapper objectMapper
    ) {
        this.llmClient = llmClient;
        this.modelRouter = modelRouter;
        this.store = store;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Async("keshTaskExecutor")
    public void evaluate(
            WorkspaceMode mode,
            String userMessage,
            RequestAnalysis analysis,
            AgentResponse response,
            String interactionId,
            RoutingDecision used
    ) {
        if (!properties.getLearning().isEnabled() || !properties.getLearning().isAsync()) {
            return;
        }
        if (response == null || response.message() == null || response.message().isBlank()) {
            return;
        }
        if (analysis != null && analysis.skipModel()) {
            return;
        }
        if (analysis != null && analysis.classification() != null
                && analysis.classification().intent() != null
                && analysis.classification().intent().writingCorrection()) {
            return;
        }
        try {
            String judgeModel = modelRouter.judgeModel();
            boolean writing = analysis != null && analysis.intent() == com.kesh.nlp.Intent.WRITING;
            LlmResponse graded = llmClient.chat(new LlmRequest(
                    judgeModel,
                    java.util.List.of(
                            LlmMessage.system(writing ? WRITING_SYSTEM : SYSTEM),
                            LlmMessage.user(prompt(userMessage, analysis, response, used))
                    ),
                    false
            ));
            Map<String, Object> scores = parse(graded);
            store.appendEvaluation(new EvaluationEvent(
                    interactionId == null ? "" : interactionId,
                    mode == null ? "work" : mode.path(),
                    used == null ? "" : used.model(),
                    judgeModel,
                    used == null ? "" : used.provider(),
                    used == null || used.role() == null ? "" : used.role().name(),
                    analysis == null || analysis.intent() == null ? "" : analysis.intent().name(),
                    scores,
                    string(scores.get("notes")),
                    Instant.now().toString()
            ));
        } catch (RuntimeException e) {
            log.info("Async judge skipped: {}", e.getMessage());
        }
    }

    private static String prompt(
            String userMessage,
            RequestAnalysis analysis,
            AgentResponse response,
            RoutingDecision used
    ) {
        String intent = analysis == null || analysis.intent() == null ? "" : analysis.intent().name();
        int tools = response.toolResults() == null ? 0 : response.toolResults().size();
        return "Intent: " + intent
                + "\nModel: " + (used == null ? "" : used.model())
                + "\nTools: " + tools
                + "\nQuestion: " + clip(userMessage, 400)
                + "\nAnswer: " + clip(response.message(), 800)
                + (analysis != null && analysis.intent() == com.kesh.nlp.Intent.WRITING
                ? "\nJudge: Did it follow the instruction? Preserve meaning and facts? Hit the requested tone? Stay natural? Avoid invented names/numbers/URLs?"
                : "");
    }

    private Map<String, Object> parse(LlmResponse graded) {
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("groundedness", 0);
        scores.put("correctness", 0);
        scores.put("completeness", 0);
        scores.put("instruction_following", 0);
        scores.put("hallucination", 0);
        scores.put("tool_use", 0);
        scores.put("notes", "");
        if (graded == null || graded.failed() || graded.content() == null || graded.content().isBlank()) {
            scores.put("notes", "judge returned no score");
            return scores;
        }
        try {
            JsonNode root = objectMapper.readTree(LlmIntentClassifierFallback.extractJson(graded.content()));
            copy(root, scores, "groundedness");
            copy(root, scores, "correctness");
            copy(root, scores, "completeness");
            copy(root, scores, "instruction_following");
            copy(root, scores, "hallucination");
            copy(root, scores, "tool_use");
            if (root.has("notes")) {
                scores.put("notes", root.path("notes").asText(""));
            }
        } catch (Exception e) {
            scores.put("notes", "judge parse failed: " + e.getMessage());
        }
        return scores;
    }

    private static void copy(JsonNode root, Map<String, Object> scores, String field) {
        if (root.has(field) && root.get(field).isNumber()) {
            scores.put(field, root.get(field).asDouble());
        }
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

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
