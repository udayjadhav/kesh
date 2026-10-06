package com.kesh.learning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;

@Component
public class LearningEventStore {

    private static final Logger log = LoggerFactory.getLogger(LearningEventStore.class);

    private final KeshProperties properties;
    private final ObjectMapper objectMapper;

    public LearningEventStore(KeshProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void appendInteraction(InteractionEvent event) {
        append(event.desk(), "interactions.jsonl", event);
    }

    public void appendEvaluation(EvaluationEvent event) {
        if (event == null) {
            return;
        }
        append(event.desk(), "evaluations.jsonl", event);
    }

    public void appendFeedback(FeedbackEvent event) {
        append(event.desk(), "feedback.jsonl", event);
        if (event == null || event.type() == null) {
            return;
        }
        if ("POSITIVE".equalsIgnoreCase(event.type())) {
            return;
        }
        createCandidate(event);
    }

    public List<LearningCandidate> pendingCandidates(String desk, int limit) {
        Map<String, String> decisions = latestDecisions(desk);
        List<LearningCandidate> pending = new ArrayList<>();
        for (JsonNode node : tailNodes(desk, "classification-candidates.jsonl", 400)) {
            LearningCandidate candidate = toCandidate(node);
            if (candidate == null || candidate.id() == null || candidate.id().isBlank()) {
                continue;
            }
            if (decisions.containsKey(candidate.id())) {
                continue;
            }
            if (candidate.status() != null && !"pending".equalsIgnoreCase(candidate.status())) {
                continue;
            }
            pending.add(candidate);
            if (pending.size() >= limit) {
                break;
            }
        }
        return pending;
    }

    public List<ApprovedLearning> approvedIntents(String desk, int limit) {
        List<ApprovedLearning> rows = new ArrayList<>();
        for (JsonNode node : tailNodes(desk, "approved-intents.jsonl", limit)) {
            rows.add(new ApprovedLearning(
                    text(node, "interactionId"),
                    text(node, "desk"),
                    text(node, "text"),
                    text(node, "predictedIntent"),
                    text(node, "correctIntent"),
                    text(node, "source"),
                    text(node, "timestamp")
            ));
        }
        return rows;
    }

    public boolean approve(String desk, String candidateId, String correctIntent) {
        LearningCandidate candidate = findCandidate(desk, candidateId);
        if (candidate == null) {
            return false;
        }
        String intent = correctIntent == null || correctIntent.isBlank()
                ? candidate.suggestedIntent()
                : correctIntent.strip();
        append(desk, "learning-decisions.jsonl", new LearningDecision(
                candidate.id(),
                safeDesk(desk),
                "approved",
                intent,
                Instant.now().toString()
        ));
        append(desk, "approved-intents.jsonl", new ApprovedLearning(
                candidate.interactionId(),
                safeDesk(desk),
                candidate.text(),
                candidate.predictedIntent(),
                intent,
                candidate.source(),
                Instant.now().toString()
        ));
        return true;
    }

    public boolean reject(String desk, String candidateId) {
        LearningCandidate candidate = findCandidate(desk, candidateId);
        if (candidate == null) {
            return false;
        }
        append(desk, "learning-decisions.jsonl", new LearningDecision(
                candidate.id(),
                safeDesk(desk),
                "rejected",
                "",
                Instant.now().toString()
        ));
        return true;
    }

    public InteractionEvent findInteraction(String desk, String interactionId) {
        if (interactionId == null || interactionId.isBlank()) {
            return null;
        }
        for (JsonNode node : tailNodes(desk, "interactions.jsonl", 500)) {
            if (interactionId.equals(text(node, "interactionId"))) {
                return new InteractionEvent(
                        text(node, "interactionId"),
                        text(node, "conversationId"),
                        text(node, "desk"),
                        text(node, "userRequest"),
                        text(node, "classifierVersion"),
                        text(node, "predictedIntent"),
                        node.path("confidence").asDouble(0),
                        List.of(),
                        List.of(),
                        text(node, "model"),
                        node.path("latencyMs").asLong(0),
                        text(node, "finalResponse"),
                        text(node, "timestamp"),
                        text(node, "knowledgeRequirement"),
                        node.path("followUp").asBoolean(false),
                        text(node, "reference"),
                        List.of(),
                        text(node, "selectedTool"),
                        text(node, "compatibilityResult")
                );
            }
        }
        return null;
    }

    private void createCandidate(FeedbackEvent event) {
        InteractionEvent interaction = findInteraction(event.desk(), event.interactionId());
        String text = interaction == null ? clip(event.comment(), 400) : interaction.userRequest();
        String predicted = interaction == null ? "" : interaction.predictedIntent();
        String suggested = suggestedIntent(event, predicted);
        append(event.desk(), "classification-candidates.jsonl", new LearningCandidate(
                UUID.randomUUID().toString(),
                event.interactionId() == null ? "" : event.interactionId(),
                safeDesk(event.desk()),
                text,
                predicted,
                suggested,
                "explicit_user_feedback",
                event.comment() == null ? "" : event.comment(),
                "pending",
                Instant.now().toString()
        ));
    }

    private LearningCandidate findCandidate(String desk, String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            return null;
        }
        for (JsonNode node : tailNodes(desk, "classification-candidates.jsonl", 500)) {
            LearningCandidate candidate = toCandidate(node);
            if (candidate != null && candidateId.equals(candidate.id())) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, String> latestDecisions(String desk) {
        Map<String, String> latest = new LinkedHashMap<>();
        List<JsonNode> nodes = readAll(desk, "learning-decisions.jsonl");
        for (JsonNode node : nodes) {
            String id = text(node, "candidateId");
            if (!id.isBlank()) {
                latest.put(id, text(node, "status"));
            }
        }
        return latest;
    }

    private LearningCandidate toCandidate(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String id = text(node, "id");
        if (id.isBlank()) {
            id = text(node, "interactionId");
        }
        String text = text(node, "text");
        if (text.isBlank()) {
            text = text(node, "comment");
        }
        return new LearningCandidate(
                id,
                text(node, "interactionId"),
                text(node, "desk"),
                text,
                text(node, "predictedIntent"),
                text(node, "suggestedIntent"),
                firstNonBlank(text(node, "source"), text(node, "type")),
                text(node, "comment"),
                firstNonBlank(text(node, "status"), "pending"),
                text(node, "timestamp")
        );
    }

    private List<JsonNode> tailNodes(String desk, String file, int limit) {
        List<JsonNode> all = readAll(desk, file);
        if (all.size() <= limit) {
            java.util.Collections.reverse(all);
            return all;
        }
        List<JsonNode> tail = new ArrayList<>(all.subList(all.size() - limit, all.size()));
        java.util.Collections.reverse(tail);
        return tail;
    }

    private List<JsonNode> readAll(String desk, String file) {
        Path path = dir(desk).resolve(file);
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        List<JsonNode> nodes = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(path)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                try {
                    nodes.add(objectMapper.readTree(line));
                } catch (IOException ignored) {
                    // skip a corrupt line; learning must not break Kesh
                }
            }
        } catch (IOException e) {
            log.info("Learning store read skip: {}", e.getMessage());
        }
        return nodes;
    }

    private void append(String desk, String file, Object event) {
        if (!properties.getLearning().isEnabled()) {
            return;
        }
        try {
            Path folder = dir(desk);
            Files.createDirectories(folder);
            Path path = folder.resolve(file);
            String line = objectMapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.info("Learning store skip: {}", e.getMessage());
        }
    }

    private Path dir(String desk) {
        return Path.of(properties.getWorkspace().getDataDir(), "learning", safeDesk(desk));
    }

    private static String suggestedIntent(FeedbackEvent event, String predicted) {
        if (event.reason() != null && event.reason().toUpperCase().contains("LLM_")) {
            return event.reason().strip();
        }
        if ("INCORRECT_INTENT".equalsIgnoreCase(event.reason())
                || "WRONG".equalsIgnoreCase(event.reason())
                || "NOT_WHAT_I_ASKED".equalsIgnoreCase(event.reason())) {
            return "";
        }
        return predicted == null ? "" : predicted;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return "";
        }
        return node.path(field).asText("");
    }

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right == null ? "" : right;
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

    private static String safeDesk(String desk) {
        if ("personal".equalsIgnoreCase(desk)) {
            return "personal";
        }
        return "work";
    }
}
