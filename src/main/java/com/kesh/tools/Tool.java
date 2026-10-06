package com.kesh.tools;

import java.util.Map;

public interface Tool {

    String name();

    String description();

    PermissionLevel permissionLevel();

    ToolSchema schema();

    ToolResult execute(Map<String, Object> arguments);
}
