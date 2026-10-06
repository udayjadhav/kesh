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
public class ListRunningAppsTool extends MacAppTool {

    public ListRunningAppsTool(ProcessRunner processRunner) {
        super(processRunner);
    }

    @Override
    public String name() {
        return "list_running_apps";
    }

    @Override
    public String description() {
        return "List visible running macOS applications.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(Map.of(), List.of());
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        return run(List.of(
                "osascript",
                "-e",
                "tell application \"System Events\" to get name of (every process whose background only is false)"
        ));
    }
}
