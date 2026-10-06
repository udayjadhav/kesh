package com.kesh.persistence;

import java.time.Instant;
import java.util.List;

import com.kesh.llm.LlmMessage;
import com.kesh.workspace.WorkspaceMode;

public record StoredConversation(
        String id,
        WorkspaceMode mode,
        String title,
        Instant createdAt,
        Instant updatedAt,
        String lastUserMessage,
        List<LlmMessage> messages
) {
}
