package com.kesh.nlp.capability;

import com.kesh.workspace.WorkspaceMode;

public record DeskContext(
        WorkspaceMode mode,
        String previousUserMessage,
        String previousAssistantMessage,
        ConversationState conversationState
) {

    public static DeskContext of(WorkspaceMode mode) {
        return new DeskContext(mode == null ? WorkspaceMode.PROFESSIONAL : mode, null, null, null);
    }

    public static DeskContext of(WorkspaceMode mode, String previousUserMessage, String previousAssistantMessage) {
        return of(mode, previousUserMessage, previousAssistantMessage, null);
    }

    public static DeskContext of(
            WorkspaceMode mode,
            String previousUserMessage,
            String previousAssistantMessage,
            ConversationState conversationState
    ) {
        return new DeskContext(
                mode == null ? WorkspaceMode.PROFESSIONAL : mode,
                previousUserMessage,
                previousAssistantMessage,
                conversationState
        );
    }

    public boolean awaitingClarification() {
        if (previousAssistantMessage == null) {
            return false;
        }
        String lower = previousAssistantMessage.toLowerCase();
        return lower.contains("i am not sure what you want")
                && lower.contains("local machine check");
    }
}
