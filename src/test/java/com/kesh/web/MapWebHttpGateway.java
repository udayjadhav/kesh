package com.kesh.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapWebHttpGateway implements WebHttpGateway {

    private final Map<String, WebHttpResponse> pages = new LinkedHashMap<>();
    private final List<URI> requested = new java.util.ArrayList<>();

    public MapWebHttpGateway putHtml(String url, String html) {
        URI uri = URI.create(url);
        pages.put(WebUrlGuard.normalize(uri), new WebHttpResponse(200, "text/html", html.getBytes(StandardCharsets.UTF_8), uri, List.of(uri)));
        return this;
    }

    public MapWebHttpGateway put(String url, int status, String type, String body) {
        URI uri = URI.create(url);
        pages.put(WebUrlGuard.normalize(uri), new WebHttpResponse(status, type, body.getBytes(StandardCharsets.UTF_8), uri, List.of(uri)));
        return this;
    }

    public List<URI> requested() {
        return List.copyOf(requested);
    }

    @Override
    public WebHttpResponse get(URI uri) {
        requested.add(uri);
        WebHttpResponse response = pages.get(WebUrlGuard.normalize(uri));
        if (response == null) {
            return new WebHttpResponse(404, "text/plain", new byte[0], uri, List.of(uri));
        }
        return response;
    }
}
