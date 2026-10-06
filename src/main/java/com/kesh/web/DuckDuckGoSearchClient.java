package com.kesh.web;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class DuckDuckGoSearchClient implements WebSearchClient {

    private final WebHttpGateway gateway;

    public DuckDuckGoSearchClient(WebHttpGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            throw new WebFetchException("Search query is required.");
        }
        int limit = Math.max(1, Math.min(10, maxResults));
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create("https://html.duckduckgo.com/html/?q=" + encoded);
        WebHttpResponse response = gateway.get(uri);
        if (!response.ok()) {
            throw new WebFetchException("Search HTTP " + response.status());
        }
        Document document = Jsoup.parse(response.bodyText(), uri.toString());
        List<WebSearchResult> results = new ArrayList<>();
        for (Element result : document.select("div.result, div.web-result, article")) {
            Element link = result.selectFirst("a.result__a, a.result-link, h2 a");
            if (link == null) {
                continue;
            }
            String href = unwrap(link.absUrl("href"));
            if (href.isBlank() || !href.startsWith("http")) {
                continue;
            }
            String title = link.text();
            String snippet = text(result.selectFirst(".result__snippet, .result-snippet, a.result__snippet"));
            String source;
            try {
                source = WebUrlGuard.host(WebUrlGuard.parse(href));
            } catch (RuntimeException e) {
                source = href;
            }
            results.add(new WebSearchResult(title, href, snippet, source, ""));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    private static String text(Element element) {
        return element == null ? "" : element.text();
    }

    private static String unwrap(String href) {
        if (href == null) {
            return "";
        }
        int uddg = href.indexOf("uddg=");
        if (uddg < 0) {
            return href;
        }
        String rest = href.substring(uddg + 5);
        int amp = rest.indexOf('&');
        if (amp >= 0) {
            rest = rest.substring(0, amp);
        }
        try {
            return URLDecoder.decode(rest, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return href;
        }
    }
}
