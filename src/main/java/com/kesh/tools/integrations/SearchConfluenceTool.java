package com.kesh.tools.integrations;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kesh.integrations.confluence.ConfluenceClient;
import com.kesh.integrations.confluence.ConfluencePage;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

public class SearchConfluenceTool implements Tool {

    private final ConfluenceClient confluenceClient;

    public SearchConfluenceTool(ConfluenceClient confluenceClient) {
        this.confluenceClient = confluenceClient;
    }

    @Override
    public String name() {
        return "search_confluence";
    }

    @Override
    public String description() {
        return "Search Confluence pages.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("query", new ToolSchema.Property("string", "Search text")),
                List.of("query")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        try {
            String query = ToolArguments.requireString(arguments, "query");
            List<ConfluencePage> pages = confluenceClient.search(query);
            String body = pages.stream()
                    .map(page -> page.space() + " / " + page.title() + " — " + page.excerpt())
                    .collect(Collectors.joining("\n"));
            if (body.isBlank()) {
                body = "No pages matched.";
            }
            return ToolResult.ok(name(), body, elapsed(started));
        } catch (IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
