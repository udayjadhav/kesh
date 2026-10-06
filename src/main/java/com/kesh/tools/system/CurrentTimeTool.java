package com.kesh.tools.system;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

@Component
public class CurrentTimeTool implements Tool {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm:ss z");

    @Override
    public String name() {
        return "current_time";
    }

    @Override
    public String description() {
        return "Return the current local date and time. Call only when the user asks what time or date it is now. Never call for greetings or to explain a previous reply.";
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
        long start = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        String text = ZonedDateTime.now(zone).format(FORMAT) + " (" + zone + ")";
        return ToolResult.ok(name(), text, System.currentTimeMillis() - start);
    }
}
