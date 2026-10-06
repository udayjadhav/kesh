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
public class OpenAppTool extends MacAppTool {

    public OpenAppTool(ProcessRunner processRunner) {
        super(processRunner);
    }

    @Override
    public String name() {
        return "open_app";
    }

    @Override
    public String description() {
        return "Open a macOS application by name, for example Google Chrome. Requires approval.";
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
            ToolResult result = run(List.of("open", "-a", name));
            if (result.success() && "OK".equals(result.output())) {
                return ToolResult.ok(name(), "Opened " + name, result.durationMs());
            }
            return result;
        } catch (IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        }
    }
}
