package com.kesh.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.kesh.tools.PermissionLevel;
import com.kesh.tools.ToolCall;
import com.kesh.workspace.WorkspaceMode;

@Component
public class ApprovalManager {

    private final ConcurrentHashMap<String, PendingApproval> pending = new ConcurrentHashMap<>();

    public PendingApproval create(String conversationId, ToolCall call, PermissionLevel level) {
        return create(WorkspaceMode.PROFESSIONAL, conversationId, call, level);
    }

    public PendingApproval create(WorkspaceMode mode, String conversationId, ToolCall call, PermissionLevel level) {
        String id = UUID.randomUUID().toString();
        PendingApproval approval = PendingApproval.from(id, mode, conversationId, call, level);
        pending.put(id, approval);
        return approval;
    }

    public PendingApproval getRequired(String id) {
        PendingApproval approval = pending.get(id);
        if (approval == null) {
            throw new IllegalArgumentException("Unknown approval request: " + id);
        }
        return approval;
    }

    public PendingApproval approve(String id) {
        return update(id, ApprovalStatus.APPROVED);
    }

    public PendingApproval reject(String id) {
        return update(id, ApprovalStatus.REJECTED);
    }

    public boolean isApproved(String id) {
        PendingApproval approval = pending.get(id);
        return approval != null && approval.status() == ApprovalStatus.APPROVED;
    }

    private PendingApproval update(String id, ApprovalStatus status) {
        PendingApproval current = getRequired(id);
        if (current.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval " + id + " is already " + current.status());
        }
        PendingApproval updated = current.withStatus(status);
        pending.put(id, updated);
        return updated;
    }
}
