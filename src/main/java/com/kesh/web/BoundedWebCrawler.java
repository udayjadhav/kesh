package com.kesh.web;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

@Component
public class BoundedWebCrawler {

    private final WebHttpGateway gateway;
    private final HtmlContentExtractor extractor;
    private final RobotsTxtPolicy robots;
    private final KeshProperties properties;

    public BoundedWebCrawler(
            WebHttpGateway gateway,
            HtmlContentExtractor extractor,
            RobotsTxtPolicy robots,
            KeshProperties properties
    ) {
        this.gateway = gateway;
        this.extractor = extractor;
        this.robots = robots;
        this.properties = properties;
    }

    public List<WebPage> crawl(String startUrl, int maxPages, int maxDepth) {
        URI start = WebUrlGuard.requirePublicHttpUrl(startUrl);
        int pagesCap = Math.max(1, Math.min(properties.getWeb().getCrawl().getMaxPages(), maxPages <= 0
                ? properties.getWeb().getCrawl().getMaxPages()
                : maxPages));
        int depthCap = Math.max(0, Math.min(properties.getWeb().getCrawl().getMaxDepth(), maxDepth < 0
                ? properties.getWeb().getCrawl().getMaxDepth()
                : maxDepth));
        boolean sameDomain = properties.getWeb().getCrawl().isSameDomainOnly();
        String robotsBody = robotsTxt(start);
        Set<String> seen = new LinkedHashSet<>();
        List<WebPage> pages = new ArrayList<>();
        Deque<CrawlItem> queue = new ArrayDeque<>();
        queue.add(new CrawlItem(start, 0));
        seen.add(WebUrlGuard.normalize(start));
        while (!queue.isEmpty() && pages.size() < pagesCap) {
            CrawlItem item = queue.removeFirst();
            if (!robots.allows(robotsBody, properties.getWeb().getUserAgent(), item.uri())) {
                continue;
            }
            WebHttpResponse response;
            try {
                response = gateway.get(item.uri());
            } catch (WebFetchException e) {
                continue;
            }
            if (!response.ok() || !extractor.acceptableType(response.contentType())) {
                continue;
            }
            WebPage page = extractor.extract(response.bodyText(), WebUrlGuard.normalize(response.url()));
            pages.add(page);
            if (item.depth() >= depthCap) {
                continue;
            }
            for (String link : page.links()) {
                URI next;
                try {
                    next = WebUrlGuard.requirePublicHttpUrl(link);
                } catch (WebFetchException e) {
                    continue;
                }
                if (sameDomain && !WebUrlGuard.sameDomain(start, next)) {
                    continue;
                }
                String key = WebUrlGuard.normalize(next);
                if (!seen.add(key)) {
                    continue;
                }
                queue.addLast(new CrawlItem(next, item.depth() + 1));
            }
        }
        return pages;
    }

    private String robotsTxt(URI start) {
        try {
            URI robotsUri = new URI(start.getScheme(), start.getUserInfo(), start.getHost(), start.getPort(), "/robots.txt", null, null);
            WebHttpResponse response = gateway.get(robotsUri);
            if (response.ok()) {
                return response.bodyText();
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private record CrawlItem(URI uri, int depth) {
    }
}
