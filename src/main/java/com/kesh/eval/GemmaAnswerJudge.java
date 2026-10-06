package com.kesh.eval;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.llm.LlmClient;
import com.kesh.llm.LlmMessage;
import com.kesh.llm.LlmRequest;
import com.kesh.llm.LlmResponse;
import com.kesh.llm.ModelRouter;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.LlmIntentClassifierFallback;
import com.kesh.tools.ToolResult;

/**
 * Local Gemma relevancy judge (DeepEval/Ragas-style answer relevancy, in-process).
 */
@Component
public class GemmaAnswerJudge implements AnswerJudge {

    private static final Logger log = LoggerFactory.getLogger(GemmaAnswerJudge.class);
    private static final String SYSTEM = """
            You grade whether an assistant answer addresses the user question.
            Return ONLY JSON: {"ok":true,"reason":"","retry_intents":[]}
            retry_intents may include WEB_SEARCH, WEB_FETCH, RUNTIME_INSPECT, FILE_SEARCH, FILE_READ, LLM_CODE_HELP, LLM_EXPLAIN.
            ok=false if the answer asks the user to pick a category, talks about Java version when they asked for a program, or refuses weather/web when the question needs live public facts.
            """;

    private final LlmClient llmClient;
    private final KeshProperties properties;
    private final ObjectMapper objectMapper;
    private final ModelRouter modelRouter;

    public GemmaAnswerJudge(LlmClient llmClient, KeshProperties properties, ObjectMapper objectMapper) {
        this(llmClient, properties, objectMapper, new ModelRouter(properties));
    }

    @Autowired
    public GemmaAnswerJudge(
            LlmClient llmClient,
            KeshProperties properties,
            ObjectMapper objectMapper,
            ModelRouter modelRouter
    ) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.modelRouter = modelRouter == null ? new ModelRouter(properties) : modelRouter;
    }

    @Override
    public JudgeVerdict evaluate(String question, String answer, RequestAnalysis analysis, List<ToolResult> tools) {
        if (!properties.getNlp().getClassifier().isJudgeEnabled()) {
            return JudgeVerdict.pass();
        }
        if (answer == null || answer.isBlank()) {
            return JudgeVerdict.retry("empty answer", List.of(CapabilityIntent.LLM_EXPLAIN));
        }
        if (looksLikeWrongClarification(question, answer)) {
            return JudgeVerdict.retry(
                    "answer asked the user to pick a category instead of doing the work",
                    retryIntents(analysis)
            );
        }
        if (looksLikeToolDump(answer) || looksLikeApologyLoop(answer)) {
            return JudgeVerdict.retry(
                    "answer pasted tools or apologized instead of answering",
                    List.of(CapabilityIntent.LLM_EXPLAIN)
            );
        }
        if (GroundingPolicy.requiresVerification(analysis)
                && GroundingPolicy.claimsUngroundedQuantity(answer, tools)) {
            if (analysis != null && analysis.allowedTools() != null
                    && analysis.allowedTools().contains("web_search")
                    && !GroundingPolicy.hasVerifiedExternalData(tools)) {
                return JudgeVerdict.retry("ungrounded live fact", List.of(CapabilityIntent.WEB_SEARCH));
            }
            return JudgeVerdict.retry("ungrounded live fact", List.of(CapabilityIntent.LLM_EXPLAIN));
        }
        if (llmClient == null || !properties.getModelRouter().isSyncJudgeLlm()) {
            return JudgeVerdict.pass();
        }
        try {
            String toolsNote = tools == null || tools.isEmpty() ? "none" : String.valueOf(tools.size());
            LlmResponse response = llmClient.chat(new LlmRequest(
                    modelRouter.judgeModel(),
                    List.of(
                            LlmMessage.system(SYSTEM),
                            LlmMessage.user("Question: " + question + "\nAnswer: " + answer + "\nTools used: " + toolsNote)
                    ),
                    false
            ));
            if (response == null || response.failed() || response.content() == null) {
                return JudgeVerdict.pass();
            }
            JsonNode root = objectMapper.readTree(LlmIntentClassifierFallback.extractJson(response.content()));
            boolean ok = root.path("ok").asBoolean(true);
            String reason = root.path("reason").asText("");
            List<CapabilityIntent> retry = new ArrayList<>();
            JsonNode array = root.get("retry_intents");
            if (array != null && array.isArray()) {
                for (JsonNode item : array) {
                    retry.add(CapabilityIntent.parse(item.asText()));
                }
            }
            if (ok) {
                return JudgeVerdict.pass();
            }
            if (retry.isEmpty()) {
                retry.addAll(retryIntents(analysis));
            }
            return JudgeVerdict.retry(reason.isBlank() ? "answer missed the question" : reason, retry);
        } catch (Exception e) {
            log.info("Answer judge skipped: {}", e.getMessage());
            return JudgeVerdict.pass();
        }
    }

    private static boolean looksLikeWrongClarification(String question, String answer) {
        if (answer == null) {
            return false;
        }
        String lower = answer.toLowerCase();
        int tokens = question == null ? 0 : question.strip().split("\\s+").length;
        if (tokens < 3) {
            return false;
        }
        return lower.contains("i am not sure what you want")
                || lower.contains("java version installed on this mac");
    }

    private static boolean looksLikeToolDump(String answer) {
        if (answer == null) {
            return false;
        }
        return answer.contains("TOOL_RESULT")
                || answer.contains("Do not invent extra URLs")
                || (answer.contains("\"results\"") && answer.contains("\"snippet\""));
    }

    private static boolean looksLikeApologyLoop(String answer) {
        if (answer == null) {
            return false;
        }
        String lower = answer.toLowerCase();
        return lower.contains("repeated misinterpretations")
                || lower.contains("you are absolutely right")
                || lower.contains("you are correct to call me out");
    }

    private static List<CapabilityIntent> retryIntents(RequestAnalysis analysis) {
        List<CapabilityIntent> retry = new ArrayList<>();
        if (analysis != null && analysis.plan() != null) {
            analysis.plan().steps().forEach(step -> retry.add(step.intent()));
        }
        if (retry.stream().noneMatch(intent -> intent.domain() == com.kesh.nlp.capability.CapabilityDomain.WEB)
                && analysis != null
                && analysis.allowedTools() != null
                && analysis.allowedTools().contains("web_search")) {
            retry.add(CapabilityIntent.WEB_SEARCH);
        }
        retry.add(CapabilityIntent.LLM_EXPLAIN);
        return retry;
    }
}
