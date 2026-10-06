package com.kesh.security;

import java.time.Instant;
import java.util.Map;

import com.kesh.tools.PermissionLevel;
import com.kesh.tools.ToolCall;
import com.kesh.workspace.WorkspaceMode;

public record PendingApproval(
        String id,
        String conversationId,
        WorkspaceMode mode,
        String tool,
        Map<String, Object> arguments,
        PermissionLevel permissionLevel,
        ApprovalStatus status,
        Instant createdAt
) {

    public PendingApproval {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        mode = mode == null ? WorkspaceMode.PROFESSIONAL : mode;
    }

    public static PendingApproval from(String id, String conversationId, ToolCall call, PermissionLevel level) {
        return from(id, WorkspaceMode.PROFESSIONAL, conversationId, call, level);
    }

    public static PendingApproval from(
            String id,
            WorkspaceMode mode,
            String conversationId,
            ToolCall call,
            PermissionLevel level
    ) {
        return new PendingApproval(
                id,
                conversationId,
                mode,
                call.tool(),
                call.arguments(),
                level,
                ApprovalStatus.PENDING,
                Instant.now()
        );
    }

    public PendingApproval withStatus(ApprovalStatus newStatus) {
        return new PendingApproval(id, conversationId, mode, tool, arguments, permissionLevel, newStatus, createdAt);
    }

    public ToolCall toToolCall() {
        return new ToolCall(id, tool, arguments);
    }
}
