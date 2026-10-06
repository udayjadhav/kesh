package com.kesh.agent;

import java.time.Instant;

/**
 * Structured conversation turn. Assistant text is a claim, never a verified fact.
 */
public record ConversationMessage(
        Role role,
        String content,
        Instant timestamp,
        String interactionId
) {
    public enum Role {
        USER,
        ASSISTANT,
        TOOL,
        SYSTEM
    }
}
