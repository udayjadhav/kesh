package com.kesh.tools.applications;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.kesh.os.ProcessRunner;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

@Component
@ConditionalOnProperty(prefix = "kesh.applications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QuitAppTool extends MacAppTool {

    public QuitAppTool(ProcessRunner processRunner) {
        super(processRunner);
    }

    @Override
    public String name() {
        return "quit_app";
    }

    @Override
    public String description() {
        return "Quit a macOS application by name. This is a dangerous action and always requires approval.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.DANGEROUS;
    }

    @Override
    public ToolSchema schema() {
        return nameSchema();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        try {
            String name = requireAppName(arguments);
            ToolResult result = run(List.of("osascript", "-e", "tell application \"" + name + "\" to quit"));
            if (result.success() && "OK".equals(result.output())) {
                return ToolResult.ok(name(), "Quit " + name, result.durationMs());
            }
            return result;
        } catch (IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        }
    }
}
