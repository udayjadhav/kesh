package com.kesh.web;

public record WebSearchResult(
        String title,
        String url,
        String snippet,
        String source,
        String publishedDate
) {
}
