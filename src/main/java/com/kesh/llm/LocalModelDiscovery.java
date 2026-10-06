package com.kesh.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;

/**
 * Lists models that are actually running locally. Never talks to a cloud LLM.
 */
@Component
public class LocalModelDiscovery {

    private static final Logger log = LoggerFactory.getLogger(LocalModelDiscovery.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final KeshProperties properties;

    public LocalModelDiscovery(HttpClient keshHttpClient, ObjectMapper objectMapper, KeshProperties properties) {
        this.httpClient = keshHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<RegisteredModel> discover() {
        if (!properties.getModelRouter().isDiscover()) {
            return List.of();
        }
        List<RegisteredModel> found = new ArrayList<>();
        found.addAll(discoverOllama(properties.getModelRouter().resolvedOllamaBaseUrl()));
        found.addAll(discoverDockerModelRunner(properties.getModelRouter().resolvedDockerModelRunnerBaseUrl()));
        return dedupe(found);
    }

    List<RegisteredModel> discoverOllama(String baseUrl) {
        JsonNode root = getJson(baseUrl, "/api/tags");
        if (root == null || !root.has("models")) {
            return List.of();
        }
        List<RegisteredModel> models = new ArrayList<>();
        for (JsonNode item : root.get("models")) {
            String name = text(item, "name", text(item, "model", ""));
            if (name.isBlank()) {
                continue;
            }
            JsonNode details = item.path("details");
            boolean tools = false;
            JsonNode caps = item.get("capabilities");
            if (caps != null && caps.isArray()) {
                for (JsonNode cap : caps) {
                    if ("tools".equalsIgnoreCase(cap.asText())) {
                        tools = true;
                    }
                }
            }
            models.add(new RegisteredModel(
                    name,
                    name,
                    "ollama",
                    "ollama",
                    trimSlash(baseUrl),
                    Set.of(),
                    details.path("context_length").asInt(0),
                    0,
                    sizeLabel(item.path("size").asLong(0)),
                    text(details, "parameter_size", ""),
                    tools,
                    true,
                    true
            ));
        }
        return models;
    }

    List<RegisteredModel> discoverDockerModelRunner(String baseUrl) {
        JsonNode root = getJson(baseUrl, "/engines/v1/models");
        if (root == null || !root.has("data")) {
            return List.of();
        }
        List<RegisteredModel> models = new ArrayList<>();
        for (JsonNode item : root.get("data")) {
            String id = text(item, "id", "");
            if (id.isBlank()) {
                continue;
            }
            JsonNode dmr = item.path("dmr");
            models.add(new RegisteredModel(
                    id,
                    id,
                    "docker-model-runner",
                    "docker-model-runner",
                    trimSlash(baseUrl),
                    Set.of(),
                    0,
                    0,
                    text(dmr, "size", ""),
                    text(dmr, "parameters", ""),
                    false,
                    true,
                    true
            ));
        }
        return models;
    }

    private JsonNode getJson(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(baseUrl) + path))
                    .timeout(Duration.ofMillis(2_000))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.info("Model discovery skipped {}{} HTTP {}", baseUrl, path, response.statusCode());
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.info("Model discovery skipped {}{}: {}", baseUrl, path, e.getMessage());
            return null;
        }
    }

    private static List<RegisteredModel> dedupe(List<RegisteredModel> models) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RegisteredModel> unique = new ArrayList<>();
        for (RegisteredModel model : models) {
            String key = model.provider() + "|" + model.model().toLowerCase();
            if (seen.add(key)) {
                unique.add(model);
            }
        }
        return unique;
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        String value = node.get(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private static String sizeLabel(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.ROOT, "%.2f GiB", gb);
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
