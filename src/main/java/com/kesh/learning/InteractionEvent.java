package com.kesh.learning;

import java.util.List;
import java.util.Map;

public record InteractionEvent(
        String interactionId,
        String conversationId,
        String desk,
        String userRequest,
        String classifierVersion,
        String predictedIntent,
        double confidence,
        List<Map<String, Object>> routingPlan,
        List<String> toolsUsed,
        String model,
        long latencyMs,
        String finalResponse,
        String timestamp,
        String knowledgeRequirement,
        boolean followUp,
        String reference,
        List<String> candidateTools,
        String selectedTool,
        String compatibilityResult
) {
}
