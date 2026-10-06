package com.kesh.agent;

import java.util.LinkedHashMap;
import java.util.Map;

import com.kesh.nlp.RequestAnalysis;
import com.kesh.security.PendingApproval;
import com.kesh.tools.ToolCall;
import com.kesh.tools.ToolResult;

public record AgentEvent(String type, Map<String, Object> data) {

    public static AgentEvent nlp(RequestAnalysis analysis) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("intent", analysis.intent().name());
        data.put("intentConfidence", Math.round(analysis.intentConfidence() * 100.0) / 100.0);
        data.put("risk", analysis.risk().name());
        data.put("riskConfidence", Math.round(analysis.riskConfidence() * 100.0) / 100.0);
        data.put("entities", analysis.entities());
        data.put("allowedTools", analysis.allowedTools());
        data.put("skipModel", analysis.skipModel());
        if (analysis.classification() != null) {
            data.put("domain", analysis.classification().domain().name());
            data.put("capability", analysis.classification().intent().name());
            data.put("classifierSource", analysis.classification().source());
            data.put("classification", analysis.classification().toMap());
        }
        if (analysis.plan() != null) {
            data.put("routing", analysis.plan().toMaps());
        }
        if (analysis.classification() != null) {
            Object knowledge = analysis.classification().entity("knowledgeRequirement");
            data.put("knowledge", knowledge == null ? "" : knowledge.toString());
            data.put("followUp", analysis.classification().flag("isFollowUp"));
            data.put("referencesPreviousContext", analysis.classification().flag("referencesPreviousContext"));
            Object reference = analysis.classification().entity("reference");
            if (reference != null) {
                data.put("reference", reference.toString());
            }
            data.put("reuseContext", analysis.classification().flag("reuseContext"));
        }
        data.put("allowedTool", analysis.allowedTools() == null || analysis.allowedTools().isEmpty()
                ? "none"
                : String.join(", ", analysis.allowedTools()));
        return new AgentEvent("nlp", data);
    }

    public static AgentEvent routing(com.kesh.routing.RoutingPlan plan) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("steps", plan == null ? java.util.List.of() : plan.toMaps());
        return new AgentEvent("routing", data);
    }

    public static AgentEvent context(com.kesh.rag.ContextAssembler.AssembledContext assembled) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (assembled != null) {
            data.put("approxTokens", assembled.approxTokens());
            data.put("sources", assembled.sourceMaps());
        }
        return new AgentEvent("context", data);
    }

    public static AgentEvent thinking(int iteration) {
        return new AgentEvent("thinking", Map.of("iteration", iteration));
    }

    public static AgentEvent token(String token) {
        return new AgentEvent("token", Map.of("text", token));
    }

    public static AgentEvent toolStart(ToolCall call) {
        return new AgentEvent("tool_start", Map.of(
                "tool", call.tool(),
                "arguments", call.arguments()
        ));
    }

    public static AgentEvent toolResult(ToolResult result) {
        return new AgentEvent("tool_result", result.toEventMap());
    }

    public static AgentEvent approvalRequired(PendingApproval approval) {
        return new AgentEvent("approval_required", Map.of(
                "id", approval.id(),
                "tool", approval.tool(),
                "arguments", approval.arguments(),
                "permissionLevel", approval.permissionLevel().name()
        ));
    }

    public static AgentEvent done(String conversationId, String message) {
        return done(conversationId, message, java.util.List.of());
    }

    public static AgentEvent done(String conversationId, String message, java.util.List<java.util.Map<String, Object>> sources) {
        return done(conversationId, message, sources, Map.of());
    }

    public static AgentEvent done(
            String conversationId,
            String message,
            java.util.List<java.util.Map<String, Object>> sources,
            Map<String, Object> writing
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId);
        data.put("message", message == null ? "" : message);
        data.put("sources", sources == null ? java.util.List.of() : sources);
        if (writing != null && !writing.isEmpty()) {
            data.put("writing", writing);
        }
        return new AgentEvent("done", data);
    }

    public static AgentEvent error(String message) {
        return new AgentEvent("error", Map.of("message", message == null ? "Unknown error" : message));
    }
}
