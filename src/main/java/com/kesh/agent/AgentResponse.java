package com.kesh.agent;

import java.util.List;
import java.util.Map;

import com.kesh.nlp.RequestAnalysis;
import com.kesh.security.PendingApproval;
import com.kesh.tools.ToolResult;

public record AgentResponse(
        Status status,
        String conversationId,
        String message,
        List<ToolResult> toolResults,
        PendingApproval approval,
        RequestAnalysis nlp,
        Map<String, Object> writing
) {

    public AgentResponse {
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        message = message == null ? "" : message;
        writing = writing == null ? Map.of() : Map.copyOf(writing);
    }

    public AgentResponse withNlp(RequestAnalysis analysis) {
        return new AgentResponse(status, conversationId, message, toolResults, approval, analysis, writing);
    }

    public AgentResponse withWriting(Map<String, Object> writingMap) {
        return new AgentResponse(status, conversationId, message, toolResults, approval, nlp, writingMap);
    }

    public enum Status {
        COMPLETED,
        APPROVAL_REQUIRED,
        MAX_ITERATIONS,
        ERROR
    }

    public static AgentResponse completed(String conversationId, String message, List<ToolResult> results) {
        return new AgentResponse(Status.COMPLETED, conversationId, message, results, null, null, Map.of());
    }

    public static AgentResponse approval(String conversationId, PendingApproval approval) {
        return new AgentResponse(
                Status.APPROVAL_REQUIRED,
                conversationId,
                "Approval required for " + approval.tool(),
                List.of(),
                approval,
                null,
                Map.of()
        );
    }

    public static AgentResponse maxIterations(String conversationId) {
        return new AgentResponse(
                Status.MAX_ITERATIONS,
                conversationId,
                "I stopped after the maximum number of tool steps. Try a more specific request.",
                List.of(),
                null,
                null,
                Map.of()
        );
    }

    public static AgentResponse error(String conversationId, String message) {
        return new AgentResponse(Status.ERROR, conversationId, message, List.of(), null, null, Map.of());
    }

    public Map<String, Object> toClientMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("status", status.name());
        map.put("conversationId", conversationId);
        map.put("message", message);
        if (approval != null) {
            map.put("tool", approval.tool());
            map.put("arguments", approval.arguments());
            map.put("approvalId", approval.id());
            map.put("permissionLevel", approval.permissionLevel().name());
        }
        if (nlp != null) {
            map.put("intent", nlp.intent().name());
            map.put("risk", nlp.risk().name());
            map.put("entities", nlp.entities());
            map.put("allowedTools", nlp.allowedTools());
            map.put("skipModel", nlp.skipModel());
            if (nlp.classification() != null) {
                map.put("domain", nlp.classification().domain().name());
                map.put("capability", nlp.classification().intent().name());
                map.put("classifierSource", nlp.classification().source());
            }
        }
        if (writing != null && !writing.isEmpty()) {
            map.put("writing", writing);
        }
        java.util.LinkedHashMap<String, java.util.Map<String, Object>> unique = new java.util.LinkedHashMap<>();
        for (ToolResult result : toolResults) {
            if (result.sources() == null) {
                continue;
            }
            for (java.util.Map<String, Object> source : result.sources()) {
                Object url = source.get("url");
                unique.putIfAbsent(url == null ? String.valueOf(source) : url.toString(), source);
            }
        }
        if (!unique.isEmpty()) {
            map.put("sources", java.util.List.copyOf(unique.values()));
        }
        return map;
    }
}
