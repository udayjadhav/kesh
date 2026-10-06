package com.kesh.security;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolCall;

@Component
public class ToolPermissionPolicy {

    private final KeshProperties properties;

    public ToolPermissionPolicy(KeshProperties properties) {
        this.properties = properties;
    }

    public PermissionDecision evaluate(Tool tool, ToolCall call) {
        if (tool == null) {
            return PermissionDecision.DENY;
        }
        if ("run_command".equals(tool.name()) && !properties.getTerminal().isEnabled()) {
            return PermissionDecision.DENY;
        }
        if ("execute_java_snippet".equals(tool.name()) && !properties.getJavaExecution().isEnabled()) {
            return PermissionDecision.DENY;
        }
        return switch (tool.permissionLevel()) {
            case READ -> PermissionDecision.ALLOW;
            case WRITE, DANGEROUS -> PermissionDecision.APPROVAL_REQUIRED;
        };
    }

    public PermissionDecision evaluateApproved(Tool tool, boolean explicitlyApproved) {
        if (tool == null) {
            return PermissionDecision.DENY;
        }
        if ("run_command".equals(tool.name()) && !properties.getTerminal().isEnabled()) {
            return PermissionDecision.DENY;
        }
        if ("execute_java_snippet".equals(tool.name()) && !properties.getJavaExecution().isEnabled()) {
            return PermissionDecision.DENY;
        }
        if (tool.permissionLevel() == PermissionLevel.READ) {
            return PermissionDecision.ALLOW;
        }
        return explicitlyApproved ? PermissionDecision.ALLOW : PermissionDecision.APPROVAL_REQUIRED;
    }
}
