package com.kesh.web;

import java.util.LinkedHashMap;
import java.util.Map;

public record WebCitation(
        String title,
        String url,
        String snippet,
        String source,
        String publishedDate,
        String retrievedAt
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (title != null && !title.isBlank()) {
            map.put("title", title);
        }
        if (url != null && !url.isBlank()) {
            map.put("url", url);
        }
        if (snippet != null && !snippet.isBlank()) {
            map.put("snippet", snippet);
        }
        if (source != null && !source.isBlank()) {
            map.put("source", source);
        }
        if (publishedDate != null && !publishedDate.isBlank()) {
            map.put("publishedDate", publishedDate);
        }
        if (retrievedAt != null && !retrievedAt.isBlank()) {
            map.put("retrievedAt", retrievedAt);
        }
        return map;
    }
}
