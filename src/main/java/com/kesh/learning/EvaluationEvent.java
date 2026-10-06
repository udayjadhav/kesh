package com.kesh.learning;

import java.util.Map;

/**
 * Async judge score. Never authorizes tools or changes policy.
 */
public record EvaluationEvent(
        String interactionId,
        String desk,
        String model,
        String judgeModel,
        String provider,
        String role,
        String predictedIntent,
        Map<String, Object> scores,
        String notes,
        String timestamp
) {
}
