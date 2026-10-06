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
public class FocusAppTool extends MacAppTool {

    public FocusAppTool(ProcessRunner processRunner) {
        super(processRunner);
    }

    @Override
    public String name() {
        return "focus_app";
    }

    @Override
    public String description() {
        return "Bring a macOS application to the front. Requires approval.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.WRITE;
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
            ToolResult result = run(List.of("osascript", "-e", "tell application \"" + name + "\" to activate"));
            if (result.success() && "OK".equals(result.output())) {
                return ToolResult.ok(name(), "Focused " + name, result.durationMs());
            }
            return result;
        } catch (IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        }
    }
}
