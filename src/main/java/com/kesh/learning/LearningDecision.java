package com.kesh.learning;

public record LearningDecision(
        String candidateId,
        String desk,
        String status,
        String correctIntent,
        String timestamp
) {
}
