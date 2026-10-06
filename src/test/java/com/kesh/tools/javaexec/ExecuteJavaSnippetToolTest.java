package com.kesh.tools.javaexec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;
import com.kesh.javaexec.JavaSandbox;
import com.kesh.javaexec.JavaSandboxResult;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.ToolResult;

class ExecuteJavaSnippetToolTest {

    @Test
    void requiresApprovalLevel() {
        ExecuteJavaSnippetTool tool = new ExecuteJavaSnippetTool(new FakeSandbox(true), new KeshProperties());
        assertThat(tool.name()).isEqualTo("execute_java_snippet");
        assertThat(tool.permissionLevel()).isEqualTo(PermissionLevel.DANGEROUS);
    }

    @Test
    void executesApprovedSnippetThroughSandbox() {
        FakeSandbox sandbox = new FakeSandbox(true);
        ExecuteJavaSnippetTool tool = new ExecuteJavaSnippetTool(sandbox, new KeshProperties());
        ToolResult result = tool.execute(Map.of("source", "System.out.println(2 + 2);"));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("4");
        assertThat(sandbox.lastSource).contains("class Main");
    }

    @Test
    void rejectsProcessEscapeBeforeSandbox() {
        FakeSandbox sandbox = new FakeSandbox(true);
        ExecuteJavaSnippetTool tool = new ExecuteJavaSnippetTool(sandbox, new KeshProperties());
        ToolResult result = tool.execute(Map.of("source", "Runtime.getRuntime().exec(\"ls\");"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not allowed");
        assertThat(sandbox.calls).isZero();
    }

    @Test
    void disabledPolicyDenies() {
        KeshProperties properties = new KeshProperties();
        properties.getJavaExecution().setEnabled(false);
        ExecuteJavaSnippetTool tool = new ExecuteJavaSnippetTool(new FakeSandbox(true), properties);
        ToolResult result = tool.execute(Map.of("source", "System.out.println(1);"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("turned off");
    }

    private static final class FakeSandbox implements JavaSandbox {
        private final boolean available;
        private int calls;
        private String lastSource;

        private FakeSandbox(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public String runtimeName() {
            return "fake";
        }

        @Override
        public JavaSandboxResult execute(String mainSource, int timeoutMs) {
            calls++;
            lastSource = mainSource;
            return new JavaSandboxResult(true, "EXECUTION", 0, "4", "", 12, "21", "fake");
        }
    }
}
