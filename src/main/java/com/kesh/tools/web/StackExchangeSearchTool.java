package com.kesh.tools.web;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

@Component
public class StackExchangeSearchTool implements Tool {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public StackExchangeSearchTool(HttpClient keshHttpClient, ObjectMapper objectMapper) {
        this.httpClient = keshHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "stackexchange_search";
    }

    @Override
    public String description() {
        return "Search Stack Overflow (free Stack Exchange API, no key for light use). Good for bugs and interview questions.";
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
            URI uri = URI.create(
                    "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&site=stackoverflow&pagesize=5&q=" + query
            );
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "KeshLocalAssistant/0.1")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                return ToolResult.fail(name(), "Stack Overflow HTTP " + response.statusCode(), System.currentTimeMillis() - start);
            }
            String encoding = response.headers().firstValue("Content-Encoding").orElse("");
            byte[] body = response.body();
            String json;
            if (encoding.toLowerCase().contains("gzip")) {
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                json = new String(body, StandardCharsets.UTF_8);
            }
            JsonNode items = objectMapper.readTree(json).path("items");
            if (!items.isArray() || items.isEmpty()) {
                return ToolResult.ok(name(), "No matching questions.", System.currentTimeMillis() - start);
            }
            StringBuilder out = new StringBuilder();
            int n = 0;
            for (JsonNode item : items) {
                out.append(item.path("title").asText())
                        .append(" (score ").append(item.path("score").asInt()).append(")\n")
                        .append(item.path("link").asText())
                        .append("\n\n");
                n++;
                if (n >= 5) {
                    break;
                }
            }
            return ToolResult.ok(name(), out.toString().trim(), System.currentTimeMillis() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.fail(name(), "Stack Overflow search interrupted", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.fail(name(), "Stack Overflow search failed: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
