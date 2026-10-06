package com.kesh.learning;

public record ApprovedLearning(
        String interactionId,
        String desk,
        String text,
        String predictedIntent,
        String correctIntent,
        String source,
        String timestamp
) {
}
