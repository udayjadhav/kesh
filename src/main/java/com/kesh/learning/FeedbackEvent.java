package com.kesh.learning;

public record FeedbackEvent(
        String interactionId,
        String conversationId,
        String desk,
        String type,
        String reason,
        String comment
) {
}
