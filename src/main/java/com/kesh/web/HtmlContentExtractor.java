package com.kesh.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

@Component
public class HtmlContentExtractor {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final KeshProperties properties;

    public HtmlContentExtractor(KeshProperties properties) {
        this.properties = properties;
    }

    public WebPage extract(String html, String url) {
        Document document = Jsoup.parse(html == null ? "" : html, url == null ? "" : url);
        document.select("script, style, noscript, iframe, svg, canvas, form, button, input, nav, footer, header, aside")
                .remove();
        document.select("[role=navigation], [role=banner], [role=contentinfo], .ad, .ads, .advert, .cookie, .popup")
                .remove();
        String title = firstNonBlank(
                document.title(),
                text(document.selectFirst("meta[property=og:title]"), "content"),
                text(document.selectFirst("h1"), null)
        );
        String description = firstNonBlank(
                text(document.selectFirst("meta[name=description]"), "content"),
                text(document.selectFirst("meta[property=og:description]"), "content")
        );
        String published = firstNonBlank(
                text(document.selectFirst("meta[property=article:published_time]"), "content"),
                text(document.selectFirst("meta[name=date]"), "content"),
                text(document.selectFirst("time[datetime]"), "datetime")
        );
        Element main = document.selectFirst("article, main, [role=main], .post, .content");
        String content = (main == null ? document.body() : main).text();
        int max = Math.max(1_000, properties.getWeb().getMaxContentChars());
        if (content.length() > max) {
            content = content.substring(0, max) + "\n…[truncated]";
        }
        Set<String> links = new LinkedHashSet<>();
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.absUrl("href");
            if (href.startsWith("http://") || href.startsWith("https://")) {
                links.add(href);
            }
            if (links.size() >= 50) {
                break;
            }
        }
        String host;
        try {
            host = WebUrlGuard.host(WebUrlGuard.parse(url));
        } catch (RuntimeException e) {
            host = url;
        }
        return new WebPage(
                url,
                title,
                description,
                content,
                List.copyOf(links),
                published,
                ISO.format(Instant.now().atOffset(ZoneOffset.UTC)),
                host
        );
    }

    public boolean acceptableType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("html")
                || lower.contains("text/")
                || lower.contains("json")
                || lower.contains("xml")
                || lower.contains("javascript");
    }

    private static String text(Element element, String attr) {
        if (element == null) {
            return "";
        }
        if (attr == null) {
            return element.text();
        }
        return element.attr(attr);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
