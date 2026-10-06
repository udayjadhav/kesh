package com.kesh.web;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BraveSearchClient implements WebSearchClient {

    private final WebHttpGateway gateway;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public BraveSearchClient(WebHttpGateway gateway, ObjectMapper objectMapper, String apiKey) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            throw new WebFetchException("Search query is required.");
        }
        int limit = Math.max(1, Math.min(10, maxResults));
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.search.brave.com/res/v1/web/search?q=" + encoded + "&count=" + limit);
        WebHttpResponse response = gateway.get(uri, Map.of("X-Subscription-Token", apiKey, "Accept", "application/json"));
        if (!response.ok()) {
            throw new WebFetchException("Brave search HTTP " + response.status());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("web").path("results");
            List<WebSearchResult> out = new ArrayList<>();
            if (results.isArray()) {
                for (JsonNode item : results) {
                    String url = item.path("url").asText("");
                    if (url.isBlank()) {
                        continue;
                    }
                    String title = item.path("title").asText("");
                    String snippet = item.path("description").asText("");
                    String source;
                    try {
                        source = WebUrlGuard.host(WebUrlGuard.parse(url));
                    } catch (RuntimeException e) {
                        source = url;
                    }
                    String published = item.path("age").asText("");
                    if (published.isBlank()) {
                        published = item.path("page_age").asText("");
                    }
                    out.add(new WebSearchResult(title, url, snippet, source, published));
                    if (out.size() >= limit) {
                        break;
                    }
                }
            }
            return out;
        } catch (WebFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new WebFetchException("Could not parse Brave search results.", e);
        }
    }
}
