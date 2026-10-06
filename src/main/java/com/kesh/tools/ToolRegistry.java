package com.kesh.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.kesh.config.SystemPromptProvider;
import com.kesh.workspace.WorkspaceMode;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final SystemPromptProvider systemPromptProvider;

    public ToolRegistry(List<Tool> toolBeans, SystemPromptProvider systemPromptProvider) {
        this.systemPromptProvider = systemPromptProvider;
        for (Tool tool : toolBeans) {
            register(tool);
        }
    }

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Tool require(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool;
    }

    public List<Tool> all() {
        return List.copyOf(tools.values());
    }

    public List<Tool> forMode(WorkspaceMode mode) {
        if (mode == null) {
            return all();
        }
        return tools.values().stream().filter(tool -> mode.allowsTool(tool.name())).toList();
    }

    public String toolsPromptSection() {
        return toolsPromptSection(all());
    }

    public String toolsPromptSection(List<Tool> catalog) {
        StringBuilder sb = new StringBuilder("Available tools:\n");
        for (Tool tool : catalog) {
            sb.append("- ").append(tool.name())
                    .append(" [").append(tool.permissionLevel()).append("]: ")
                    .append(tool.description())
                    .append("\n  arguments: ").append(tool.schema().toMap())
                    .append("\n");
        }
        return sb.toString();
    }

    public String systemPrompt() {
        return systemPromptProvider.template();
    }

    public String systemPrompt(WorkspaceMode mode) {
        return systemPrompt();
    }

    public String compactToolsSection(java.util.Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("This turn you may call: ");
        sb.append(String.join(", ", names)).append('.');
        return sb.toString();
    }

    public List<Map<String, Object>> openaiToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.schema().toMap());
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            def.put("function", function);
            definitions.add(def);
        }
        return definitions;
    }
}
