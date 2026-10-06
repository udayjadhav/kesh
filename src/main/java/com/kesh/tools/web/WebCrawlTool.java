package com.kesh.tools.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;
import com.kesh.web.BoundedWebCrawler;
import com.kesh.web.WebCitation;
import com.kesh.web.WebFetchException;
import com.kesh.web.WebPage;

@Component
public class WebCrawlTool implements Tool {

    private final BoundedWebCrawler crawler;
    private final KeshProperties properties;

    public WebCrawlTool(BoundedWebCrawler crawler, KeshProperties properties) {
        this.crawler = crawler;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "web_crawl";
    }

    @Override
    public String description() {
        return "Fetch a small set of public pages from one site. Same domain only. Honors robots.txt, max pages, and max depth. "
                + "Use only when one page is not enough. Arguments: url, maxPages (optional), maxDepth (optional).";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of(
                        "url", new ToolSchema.Property("string", "Starting public http or https URL"),
                        "maxPages", new ToolSchema.Property("integer", "Page cap, default 10"),
                        "maxDepth", new ToolSchema.Property("integer", "Link depth cap, default 2")
                ),
                List.of("url")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.currentTimeMillis();
        if (!properties.getWeb().crawlAvailable()) {
            return ToolResult.fail(name(), "Web crawl is turned off.", System.currentTimeMillis() - started);
        }
        try {
            String url = ToolArguments.requireString(arguments, "url");
            int maxPages = ToolArguments.optionalInt(arguments, "maxPages", properties.getWeb().getCrawl().getMaxPages());
            int maxDepth = ToolArguments.optionalInt(arguments, "maxDepth", properties.getWeb().getCrawl().getMaxDepth());
            List<WebPage> pages = crawler.crawl(url, maxPages, maxDepth);
            if (pages.isEmpty()) {
                return ToolResult.fail(name(), "Crawl found no readable pages at " + url, System.currentTimeMillis() - started);
            }
            StringBuilder text = new StringBuilder("Crawled ").append(pages.size()).append(" pages from ").append(url).append(".\n\n");
            List<Map<String, Object>> sources = new ArrayList<>();
            int index = 1;
            for (WebPage page : pages) {
                sources.add(new WebCitation(
                        page.title(),
                        page.url(),
                        page.description(),
                        page.source(),
                        page.publishedDate(),
                        page.retrievedAt()
                ).toMap());
                text.append("=== Page ").append(index++).append(" ===\n");
                text.append("url: ").append(page.url()).append('\n');
                text.append("title: ").append(page.title()).append('\n');
                text.append(page.content()).append("\n\n");
            }
            text.append("Do not invent extra URLs. Quote these sources if you use them.");
            return ToolResult.ok(name(), text.toString(), System.currentTimeMillis() - started, sources);
        } catch (WebFetchException e) {
            return ToolResult.fail(name(), e.getMessage(), System.currentTimeMillis() - started);
        } catch (Exception e) {
            return ToolResult.fail(name(), "Crawl failed: " + e.getMessage(), System.currentTimeMillis() - started);
        }
    }
}
