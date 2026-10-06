package com.kesh.nlp.capability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.llm.LlmClient;
import com.kesh.llm.LlmMessage;
import com.kesh.llm.LlmRequest;
import com.kesh.llm.LlmResponse;

/**
 * Structured Gemma classification. Gemma may only name domain/intent/entities.
 */
public class LlmIntentClassifierFallback implements CapabilityClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmIntentClassifierFallback.class);

    private static final String SYSTEM = systemPrompt();

    private static String systemPrompt() {
        String domains = Arrays.stream(CapabilityDomain.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        String intents = Arrays.stream(CapabilityIntent.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return """
                You classify Kesh user messages. Return ONLY JSON, no markdown.
                Schema: {"intents":[{"domain":"WEB","intent":"WEB_SEARCH","confidence":0.9,"entities":{"query":"..."}}]}
                Domains: %s
                Intents: %s
                Rules:
                - How-to / write-code / explain-code asks are LLM_CODE_HELP, not TERMINAL_EXECUTION or RUNTIME_INSPECT.
                - Imperative run/execute of a shell command is TERMINAL_EXECUTION.
                - Imperative open/launch an app is APP_OPEN. How to open an app is LLM_CODE_HELP.
                - Imperative fix grammar/spelling/punctuation is WRITING_CORRECT_GRAMMAR / SPELLING / FIX_PUNCTUATION.
                - Imperative format this message / clean up this Slack note is WRITING_FORMAT, not LLM_FORMAT.
                - LLM_FORMAT is tables, bullets, and checklists.
                - Imperative rewrite, shorten, naturalize, or change tone is the matching WRITING_* intent.
                - Draft email/slack/jira/confluence/PR/reply is WRITING_DRAFT_*.
                - How do I write / how should I reply / how can I fix grammar is LLM_EXPLAIN, not WRITING.
                - Summarize above/that/previous answer is WRITING_SUMMARIZE, not WEB_SEARCH.
                - Tell me about / explain / what is without latest/current/search is LLM_EXPLAIN. Technical does not mean web.
                - Public latest/current version, weather, traffic, news, and explicit web search are WEB_SEARCH (plus LLM_EXPLAIN when the user also wants an explanation).
                - Installed-on-this-Mac version checks are RUNTIME_INSPECT.
                - Return every capability needed. Web + LLM, system + LLM, files + web + LLM are valid.
                - Do not include allowed, permission, policy, or skip fields.
                """.formatted(domains, intents);
    }

    private final LlmClient llmClient;
    private final KeshProperties properties;
    private final ObjectMapper objectMapper;

    public LlmIntentClassifierFallback(LlmClient llmClient, KeshProperties properties, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "gemma_fallback";
    }

    @Override
    public ClassificationResult classify(String text, DeskContext context) {
        if (llmClient == null || text == null || text.isBlank()) {
            return ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.2, Map.of(), name());
        }
        try {
            LlmResponse response = llmClient.chat(new LlmRequest(
                    properties.getLlm().getModel(),
                    List.of(LlmMessage.system(SYSTEM), LlmMessage.user(text)),
                    false
            ));
            if (response == null || response.failed() || response.content() == null) {
                return ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.2, Map.of(), name());
            }
            JsonNode root = objectMapper.readTree(extractJson(response.content()));
            List<ClassifiedIntent> intents = parseIntents(root, text);
            if (intents.isEmpty()) {
                return ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.3, Map.of(), name());
            }
            return new ClassificationResult(intents, name(), false, null);
        } catch (Exception e) {
            log.info("Gemma classification fallback failed: {}", e.getMessage());
            return ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.2, Map.of(), name());
        }
    }

    private List<ClassifiedIntent> parseIntents(JsonNode root, String text) {
        List<ClassifiedIntent> intents = new ArrayList<>();
        JsonNode array = root.get("intents");
        if (array == null || !array.isArray()) {
            CapabilityIntent intent = CapabilityIntent.parse(text(root, "intent"));
            double confidence = root.path("confidence").asDouble(0.7);
            intents.add(new ClassifiedIntent(intent.domain(), intent, confidence, entities(root.get("entities"), text, intent)));
            return intents;
        }
        for (JsonNode item : array) {
            CapabilityIntent intent = CapabilityIntent.parse(text(item, "intent"));
            double confidence = item.path("confidence").asDouble(0.7);
            intents.add(new ClassifiedIntent(intent.domain(), intent, confidence, entities(item.get("entities"), text, intent)));
        }
        return intents;
    }

    private Map<String, Object> entities(JsonNode node, String text, CapabilityIntent intent) {
        Map<String, Object> entities = new LinkedHashMap<>(OpenNlpCapabilityClassifier.entitiesFor(text, intent));
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (!"allowed".equals(entry.getKey()) && !"permission".equals(entry.getKey())) {
                    entities.put(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        return entities;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    public static String extractJson(String content) {
        String stripped = content.strip();
        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return stripped.substring(start, end + 1);
        }
        return stripped;
    }
}
