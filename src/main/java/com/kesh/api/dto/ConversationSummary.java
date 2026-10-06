package com.kesh.api.dto;

import java.time.Instant;

public record ConversationSummary(String id, String title, Instant updatedAt) {
}
