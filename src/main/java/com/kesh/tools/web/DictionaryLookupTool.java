package com.kesh.tools.web;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
public class DictionaryLookupTool implements Tool {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DictionaryLookupTool(HttpClient keshHttpClient, ObjectMapper objectMapper) {
        this.httpClient = keshHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "dictionary_lookup";
    }

    @Override
    public String description() {
        return "Look up an English word (Free Dictionary API, no key). Personal desk learning helper.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("word", new ToolSchema.Property("string", "Word to look up")),
                List.of("word")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        try {
            String word = URLEncoder.encode(ToolArguments.requireString(arguments, "word"), StandardCharsets.UTF_8);
            URI uri = URI.create("https://api.dictionaryapi.dev/api/v2/entries/en/" + word);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "KeshLocalAssistant/0.1")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return ToolResult.fail(name(), "No definition found.", System.currentTimeMillis() - start);
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                return ToolResult.fail(name(), "No definition found.", System.currentTimeMillis() - start);
            }
            JsonNode entry = root.get(0);
            StringBuilder out = new StringBuilder();
            out.append(entry.path("word").asText()).append('\n');
            JsonNode meanings = entry.path("meanings");
            int count = 0;
            for (JsonNode meaning : meanings) {
                String part = meaning.path("partOfSpeech").asText("");
                JsonNode defs = meaning.path("definitions");
                if (defs.isArray() && !defs.isEmpty()) {
                    out.append(part).append(": ").append(defs.get(0).path("definition").asText()).append('\n');
                    count++;
                }
                if (count >= 4) {
                    break;
                }
            }
            return ToolResult.ok(name(), out.toString().trim(), System.currentTimeMillis() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.fail(name(), "Dictionary lookup interrupted", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.fail(name(), "Dictionary lookup failed: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
