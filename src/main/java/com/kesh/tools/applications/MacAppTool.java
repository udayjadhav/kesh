package com.kesh.tools.applications;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.kesh.os.ProcessResult;
import com.kesh.os.ProcessRunner;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

abstract class MacAppTool implements Tool {

    private static final Pattern APP_NAME = Pattern.compile("^[A-Za-z0-9 .'+\\-]{1,80}$");

    protected final ProcessRunner processRunner;

    protected MacAppTool(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    protected static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac");
    }

    protected String requireAppName(Map<String, Object> arguments) {
        String name = ToolArguments.requireString(arguments, "name");
        if (!APP_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid application name");
        }
        return name;
    }

    protected ToolResult run(List<String> command) {
        long started = System.nanoTime();
        if (!isMac()) {
            return ToolResult.fail(name(), "Application tools are only available on macOS", elapsed(started));
        }
        ProcessResult result = processRunner.run(command, Duration.ofSeconds(15));
        if (!result.succeeded()) {
            String detail = result.combinedOutput();
            return ToolResult.fail(
                    name(),
                    detail.isBlank() ? "Command failed with exit " + result.exitCode() : detail,
                    elapsed(started)
            );
        }
        String output = result.combinedOutput();
        if (output.isBlank()) {
            output = "OK";
        }
        return ToolResult.ok(name(), output, elapsed(started));
    }

    protected static ToolSchema nameSchema() {
        return ToolSchema.object(
                Map.of("name", new ToolSchema.Property("string", "macOS application name, e.g. Google Chrome")),
                List.of("name")
        );
    }

    protected static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
