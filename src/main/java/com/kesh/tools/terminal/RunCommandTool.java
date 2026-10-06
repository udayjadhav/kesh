package com.kesh.tools.terminal;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.os.ProcessResult;
import com.kesh.os.ProcessRunner;
import com.kesh.security.CommandSafetyPolicy;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

@Component
@ConditionalOnProperty(prefix = "kesh.terminal", name = "enabled", havingValue = "true")
public class RunCommandTool implements Tool {

    private final ProcessRunner processRunner;
    private final CommandSafetyPolicy safetyPolicy;
    private final Duration timeout;

    public RunCommandTool(
            ProcessRunner processRunner,
            CommandSafetyPolicy safetyPolicy,
            KeshProperties properties
    ) {
        this.processRunner = processRunner;
        this.safetyPolicy = safetyPolicy;
        this.timeout = Duration.ofSeconds(properties.getTerminal().getTimeoutSeconds());
    }

    @Override
    public String name() {
        return "run_command";
    }

    @Override
    public String description() {
        return "Run an allowlisted command. Disabled by default. Dangerous. Always requires approval. No shell metacharacters.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.DANGEROUS;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("command", new ToolSchema.Property("string", "Allowlisted command and arguments, for example git status")),
                List.of("command")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        try {
            String raw = ToolArguments.requireString(arguments, "command");
            List<String> tokens = safetyPolicy.tokenizeAndValidate(raw);
            ProcessResult result = processRunner.run(tokens, timeout);
            String output = result.combinedOutput();
            if (!result.succeeded()) {
                return ToolResult.fail(
                        name(),
                        output.isBlank() ? "Exit " + result.exitCode() : output,
                        elapsed(started)
                );
            }
            return ToolResult.ok(name(), output.isBlank() ? "(no output)" : output, elapsed(started));
        } catch (SecurityException | IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
