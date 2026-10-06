package com.kesh.tools.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;
import com.kesh.web.WebCitation;
import com.kesh.web.WebFetchException;
import com.kesh.web.WebSearchClient;
import com.kesh.web.WebSearchResult;

@Component
public class WebSearchTool implements Tool {

    private final WebSearchClient searchClient;
    private final KeshProperties properties;

    public WebSearchTool(WebSearchClient webSearchClient, KeshProperties properties) {
        this.searchClient = webSearchClient;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "Search the public web for current information. Use for latest releases, news, and facts you cannot know locally. "
                + "Arguments: query (required), max_results (optional, default 5).";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of(
                        "query", new ToolSchema.Property("string", "Search query"),
                        "max_results", new ToolSchema.Property("integer", "How many results, 1-10")
                ),
                List.of("query")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.currentTimeMillis();
        if (!properties.getWeb().searchAvailable()) {
            return ToolResult.fail(name(), "Web search is turned off.", System.currentTimeMillis() - started);
        }
        try {
            String query = ToolArguments.requireString(arguments, "query");
            int max = ToolArguments.optionalInt(arguments, "max_results", properties.getWeb().getSearch().getMaxResults());
            List<WebSearchResult> results = searchClient.search(query, max);
            if (results.isEmpty()) {
                return ToolResult.ok(name(), "No search results for: " + query, System.currentTimeMillis() - started);
            }
            String retrievedAt = Instant.now().toString();
            String executionId = java.util.UUID.randomUUID().toString();
            List<Map<String, Object>> sources = new ArrayList<>();
            StringBuilder text = new StringBuilder("Found ").append(results.size()).append(" sources for \"")
                    .append(query).append("\":\n\n");
            int i = 1;
            for (WebSearchResult result : results) {
                Map<String, Object> source = new WebCitation(
                        result.title(),
                        result.url(),
                        result.snippet(),
                        result.source(),
                        result.publishedDate(),
                        retrievedAt
                ).toMap();
                source.put("toolExecutionId", executionId);
                source.put("sourceKind", "WEB_SEARCH");
                sources.add(source);
                text.append(i++).append(". ").append(blank(result.title(), result.url())).append('\n');
                text.append("   ").append(result.url()).append('\n');
                if (result.snippet() != null && !result.snippet().isBlank()) {
                    String snippet = result.snippet().strip();
                    if (snippet.length() > 280) {
                        snippet = snippet.substring(0, 280) + "…";
                    }
                    text.append("   ").append(snippet).append('\n');
                }
                text.append('\n');
            }
            return ToolResult.ok(name(), text.toString().strip(), System.currentTimeMillis() - started, sources);
        } catch (WebFetchException e) {
            return ToolResult.fail(name(), e.getMessage(), System.currentTimeMillis() - started);
        } catch (Exception e) {
            return ToolResult.fail(name(), "Search failed: " + e.getMessage(), System.currentTimeMillis() - started);
        }
    }

    private static String blank(String title, String fallback) {
        return title == null || title.isBlank() ? fallback : title;
    }
}
