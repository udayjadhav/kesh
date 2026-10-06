package com.kesh.tools.format;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

@Component
public class FormatJsonTool implements Tool {

    private final ObjectMapper objectMapper;

    public FormatJsonTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "format_json";
    }

    @Override
    public String description() {
        return "Pretty-print JSON text locally. No network and no shell.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("text", new ToolSchema.Property("string", "Raw JSON")),
                List.of("text")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        try {
            String text = ToolArguments.requireString(arguments, "text");
            JsonNode node = objectMapper.readTree(text);
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            return ToolResult.ok(name(), pretty, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.fail(name(), "Invalid JSON: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
