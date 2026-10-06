package com.kesh.learning;

public record LearningCandidate(
        String id,
        String interactionId,
        String desk,
        String text,
        String predictedIntent,
        String suggestedIntent,
        String source,
        String comment,
        String status,
        String timestamp
) {
}
