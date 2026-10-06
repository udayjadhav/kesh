package com.kesh.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;
import com.kesh.tools.FakeTool;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.ToolCall;

class ToolPermissionPolicyTest {

    @Test
    void readIsAutomaticallyAllowed() {
        ToolPermissionPolicy policy = new ToolPermissionPolicy(new KeshProperties());
        FakeTool read = new FakeTool("read_file", PermissionLevel.READ);
        assertThat(policy.evaluate(read, ToolCall.of("read_file", java.util.Map.of())))
                .isEqualTo(PermissionDecision.ALLOW);
    }

    @Test
    void writeRequiresApproval() {
        ToolPermissionPolicy policy = new ToolPermissionPolicy(new KeshProperties());
        FakeTool write = new FakeTool("open_app", PermissionLevel.WRITE);
        assertThat(policy.evaluateApproved(write, false)).isEqualTo(PermissionDecision.APPROVAL_REQUIRED);
        assertThat(policy.evaluateApproved(write, true)).isEqualTo(PermissionDecision.ALLOW);
    }

    @Test
    void javaSnippetRequiresApprovalAndCanBeDisabled() {
        ToolPermissionPolicy policy = new ToolPermissionPolicy(new KeshProperties());
        FakeTool java = new FakeTool("execute_java_snippet", PermissionLevel.DANGEROUS);
        assertThat(policy.evaluateApproved(java, false)).isEqualTo(PermissionDecision.APPROVAL_REQUIRED);
        KeshProperties off = new KeshProperties();
        off.getJavaExecution().setEnabled(false);
        ToolPermissionPolicy denied = new ToolPermissionPolicy(off);
        assertThat(denied.evaluateApproved(java, true)).isEqualTo(PermissionDecision.DENY);
    }
}
