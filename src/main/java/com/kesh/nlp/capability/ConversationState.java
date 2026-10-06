package com.kesh.nlp.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight per-conversation memory for follow-ups. Never overrides an explicit user instruction.
 */
public final class ConversationState {

    private String topic = "";
    private final List<String> entities = new ArrayList<>();
    private String timeContext = "";
    private String lastIntent = "";
    private String lastCapability = "";
    private String lastTool = "";
    private String lastResponseId = "";
    private String lastKnowledge = "";
    private boolean lastUsedWeb;
    private String lastWebQuery = "";
    private final List<Map<String, Object>> lastResults = new ArrayList<>();

    public String topic() {
        return topic;
    }

    public List<String> entities() {
        return List.copyOf(entities);
    }

    public String timeContext() {
        return timeContext;
    }

    public String lastIntent() {
        return lastIntent;
    }

    public String lastCapability() {
        return lastCapability;
    }

    public String lastTool() {
        return lastTool;
    }

    public String lastResponseId() {
        return lastResponseId;
    }

    public String lastKnowledge() {
        return lastKnowledge;
    }

    public boolean lastUsedWeb() {
        return lastUsedWeb;
    }

    public String lastWebQuery() {
        return lastWebQuery;
    }

    public List<Map<String, Object>> lastResults() {
        return List.copyOf(lastResults);
    }

    public Map<String, Object> result(int index) {
        if (index < 0 || index >= lastResults.size()) {
            return Map.of();
        }
        return lastResults.get(index);
    }

    public synchronized void recordTurn(
            String topic,
            List<String> entities,
            String timeContext,
            String lastIntent,
            String lastCapability,
            String lastTool,
            String lastResponseId,
            String lastKnowledge,
            boolean lastUsedWeb,
            String lastWebQuery,
            List<Map<String, Object>> lastResults
    ) {
        this.topic = topic == null ? "" : topic;
        this.entities.clear();
        if (entities != null) {
            for (String entity : entities) {
                if (entity != null && !entity.isBlank()) {
                    this.entities.add(entity.strip());
                }
            }
        }
        this.timeContext = timeContext == null ? "" : timeContext;
        this.lastIntent = lastIntent == null ? "" : lastIntent;
        this.lastCapability = lastCapability == null ? "" : lastCapability;
        this.lastTool = lastTool == null ? "" : lastTool;
        this.lastResponseId = lastResponseId == null ? "" : lastResponseId;
        this.lastKnowledge = lastKnowledge == null ? "" : lastKnowledge;
        this.lastUsedWeb = lastUsedWeb;
        this.lastWebQuery = lastWebQuery == null ? "" : lastWebQuery;
        this.lastResults.clear();
        if (lastResults != null) {
            this.lastResults.addAll(lastResults);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("topic", topic);
        map.put("entities", List.copyOf(entities));
        map.put("timeContext", timeContext);
        map.put("lastIntent", lastIntent);
        map.put("lastCapability", lastCapability);
        map.put("lastTool", lastTool);
        map.put("lastResponseId", lastResponseId);
        map.put("lastKnowledge", lastKnowledge);
        map.put("lastUsedWeb", lastUsedWeb);
        map.put("lastWebQuery", lastWebQuery);
        map.put("lastResultCount", lastResults.size());
        return map;
    }
}
