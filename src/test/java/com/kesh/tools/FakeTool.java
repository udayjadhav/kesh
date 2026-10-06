package com.kesh.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeTool implements Tool {

    private final String name;
    private final PermissionLevel permissionLevel;
    private final List<Map<String, Object>> calls = new ArrayList<>();
    private final AtomicInteger executions = new AtomicInteger();
    private ToolResult result = ToolResult.ok("fake", "ok", 1);
    private RuntimeException failure;

    public FakeTool(String name, PermissionLevel permissionLevel) {
        this.name = name;
        this.permissionLevel = permissionLevel;
    }

    public FakeTool succeeding(String output) {
        this.result = ToolResult.ok(name, output, 1);
        return this;
    }

    public FakeTool failing(String error) {
        this.result = ToolResult.fail(name, error, 1);
        return this;
    }

    public FakeTool throwing(RuntimeException error) {
        this.failure = error;
        return this;
    }

    public int executions() {
        return executions.get();
    }

    public List<Map<String, Object>> calls() {
        return List.copyOf(calls);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return "Fake tool " + name;
    }

    @Override
    public PermissionLevel permissionLevel() {
        return permissionLevel;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(Map.of("q", new ToolSchema.Property("string", "query")), List.of());
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        executions.incrementAndGet();
        calls.add(arguments);
        if (failure != null) {
            throw failure;
        }
        return result;
    }
}
