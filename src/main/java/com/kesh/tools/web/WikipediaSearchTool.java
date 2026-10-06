package com.kesh.tools.web;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
public class WikipediaSearchTool implements Tool {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WikipediaSearchTool(HttpClient keshHttpClient, ObjectMapper objectMapper) {
        this.httpClient = keshHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "wikipedia_search";
    }

    @Override
    public String description() {
        return "Search Wikipedia (free public API, no key). Use on the personal desk for learning.";
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
        long start = System.currentTimeMillis();
        try {
            String query = URLEncoder.encode(ToolArguments.requireString(arguments, "query"), StandardCharsets.UTF_8);
            URI uri = URI.create("https://en.wikipedia.org/w/api.php?action=opensearch&limit=5&namespace=0&format=json&search=" + query);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "KeshLocalAssistant/0.1")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return ToolResult.fail(name(), "Wikipedia HTTP " + response.statusCode(), System.currentTimeMillis() - start);
            }
            JsonNode node = objectMapper.readTree(response.body());
            List<String> titles = toList(node.get(1));
            List<String> blurbs = toList(node.get(2));
            List<String> urls = toList(node.get(3));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < titles.size(); i++) {
                out.append(titles.get(i));
                if (i < blurbs.size() && !blurbs.get(i).isBlank()) {
                    out.append(" — ").append(blurbs.get(i));
                }
                if (i < urls.size()) {
                    out.append('\n').append(urls.get(i));
                }
                out.append("\n\n");
            }
            return ToolResult.ok(name(), out.toString().trim(), System.currentTimeMillis() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.fail(name(), "Wikipedia search interrupted", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.fail(name(), "Wikipedia search failed: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private static List<String> toList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            values.add(item.asText(""));
        }
        return values;
    }
}
