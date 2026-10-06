package com.kesh.web;

import java.util.List;

public record WebPage(
        String url,
        String title,
        String description,
        String content,
        List<String> links,
        String publishedDate,
        String retrievedAt,
        String source
) {
}
