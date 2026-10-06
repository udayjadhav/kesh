package com.kesh.tools.applications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kesh.os.ProcessResult;
import com.kesh.os.ProcessRunner;
import com.kesh.tools.ToolResult;

class ApplicationToolsTest {

    private final RecordingRunner runner = new RecordingRunner();

    @Test
    void listRunningAppsUsesOsascript() {
        runner.next = new ProcessResult(0, "Google Chrome, Finder, Cursor", "");
        ListRunningAppsTool tool = new ListRunningAppsTool(runner);
        ToolResult result = tool.execute(Map.of());
        if (!isMac()) {
            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("macOS");
            return;
        }
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Google Chrome");
        assertThat(runner.commands.get(0).get(0)).isEqualTo("osascript");
    }

    @Test
    void openAppUsesOpenDashA() {
        runner.next = new ProcessResult(0, "", "");
        OpenAppTool tool = new OpenAppTool(runner);
        ToolResult result = tool.execute(Map.of("name", "Google Chrome"));
        if (!isMac()) {
            assertThat(result.success()).isFalse();
            return;
        }
        assertThat(result.success()).isTrue();
        assertThat(runner.commands.get(0)).containsExactly("open", "-a", "Google Chrome");
        assertThat(result.output()).contains("Opened Google Chrome");
    }

    @Test
    void quitAppUsesOsascript() {
        runner.next = new ProcessResult(0, "", "");
        QuitAppTool tool = new QuitAppTool(runner);
        ToolResult result = tool.execute(Map.of("name", "Google Chrome"));
        if (!isMac()) {
            assertThat(result.success()).isFalse();
            return;
        }
        assertThat(result.success()).isTrue();
        assertThat(runner.commands.get(0).get(0)).isEqualTo("osascript");
        assertThat(runner.commands.get(0).get(2)).contains("Google Chrome");
        assertThat(result.output()).contains("Quit Google Chrome");
    }

    @Test
    void rejectsUnsafeAppName() {
        OpenAppTool tool = new OpenAppTool(runner);
        ToolResult result = tool.execute(Map.of("name", "Chrome; rm -rf /"));
        assertThat(result.success()).isFalse();
        assertThat(runner.commands).isEmpty();
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static final class RecordingRunner implements ProcessRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private ProcessResult next = new ProcessResult(0, "", "");

        @Override
        public ProcessResult run(List<String> command, Duration timeout) {
            commands.add(List.copyOf(command));
            return next;
        }
    }
}
