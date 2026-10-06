package com.kesh.observability;

import java.time.Instant;
import java.util.Map;

import com.kesh.tools.PermissionLevel;

public record AuditEvent(
        Instant timestamp,
        String conversationId,
        String userMessage,
        String model,
        String tool,
        Map<String, Object> arguments,
        PermissionLevel permissionLevel,
        String approvalStatus,
        long executionDurationMs,
        String toolResult,
        String error
) {
}
