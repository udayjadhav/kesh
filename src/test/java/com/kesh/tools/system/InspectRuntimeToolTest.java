package com.kesh.tools.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kesh.os.ProcessResult;
import com.kesh.os.ProcessRunner;
import com.kesh.tools.ToolResult;

class InspectRuntimeToolTest {

    @Test
    void javaVersionUsesJavaDashVersion() {
        RecordingRunner runner = new RecordingRunner();
        runner.next = new ProcessResult(0, "", "openjdk version \"21.0.9\" 2025-10-21");
        InspectRuntimeTool tool = new InspectRuntimeTool(runner);
        ToolResult result = tool.execute(Map.of("name", "java"));
        assertThat(result.success()).isTrue();
        assertThat(runner.commands.get(0)).containsExactly("java", "-version");
        assertThat(result.output()).contains("21.0.9");
        assertThat(result.output()).contains("java -version");
    }

    @Test
    void nodeAliasFromNodejs() {
        RecordingRunner runner = new RecordingRunner();
        runner.next = new ProcessResult(0, "v22.11.0", "");
        InspectRuntimeTool tool = new InspectRuntimeTool(runner);
        ToolResult result = tool.execute(Map.of("name", "nodejs"));
        assertThat(result.success()).isTrue();
        assertThat(runner.commands.get(0)).containsExactly("node", "-v");
        assertThat(result.output()).contains("v22.11.0");
    }

    @Test
    void missingRuntimeIsAClearAnswer() {
        RecordingRunner runner = new RecordingRunner();
        runner.next = new ProcessResult(1, "", "Cannot run program \"node\": error=2, No such file or directory");
        InspectRuntimeTool tool = new InspectRuntimeTool(runner);
        ToolResult result = tool.execute(Map.of("name", "node"));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("not installed");
    }

    @Test
    void unknownRuntimeIsRejected() {
        RecordingRunner runner = new RecordingRunner();
        InspectRuntimeTool tool = new InspectRuntimeTool(runner);
        ToolResult result = tool.execute(Map.of("name", "rm"));
        assertThat(result.success()).isFalse();
        assertThat(runner.commands).isEmpty();
    }

    @Test
    void macosUsesSwVers() {
        RecordingRunner runner = new RecordingRunner();
        runner.next = new ProcessResult(0, "ProductName: macOS\nProductVersion: 26.6.0\nBuildVersion: 25G123", "");
        InspectRuntimeTool tool = new InspectRuntimeTool(runner);
        ToolResult result = tool.execute(Map.of("name", "mac"));
        assertThat(result.success()).isTrue();
        assertThat(runner.commands.get(0)).containsExactly("sw_vers");
        assertThat(result.output()).contains("26.6.0");
    }

    @Test
    void cursorFallsBackToAppPlist() {
        RecordingRunner runner = new RecordingRunner();
        runner.queue(
                new ProcessResult(1, "", "Cannot run program \"cursor\""),
                new ProcessResult(0, "1.7.2", "")
        );
        InspectRuntimeTool tool = new InspectRuntimeTool(runner);
        ToolResult result = tool.execute(Map.of("name", "cursor"));
        assertThat(result.success()).isTrue();
        assertThat(runner.commands.get(0)).containsExactly("cursor", "--version");
        assertThat(runner.commands.get(1).get(0)).isEqualTo("defaults");
        assertThat(result.output()).contains("1.7.2");
    }

    private static final class RecordingRunner implements ProcessRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private final List<ProcessResult> queued = new ArrayList<>();
        private ProcessResult next = new ProcessResult(0, "", "");

        void queue(ProcessResult... results) {
            queued.addAll(List.of(results));
        }

        @Override
        public ProcessResult run(List<String> command, Duration timeout) {
            commands.add(List.copyOf(command));
            if (!queued.isEmpty()) {
                return queued.remove(0);
            }
            return next;
        }
    }
}
