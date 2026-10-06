package com.kesh.tools.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;
import com.kesh.web.HtmlContentExtractor;
import com.kesh.web.WebCitation;
import com.kesh.web.WebFetchException;
import com.kesh.web.WebHttpGateway;
import com.kesh.web.WebHttpResponse;
import com.kesh.web.WebPage;
import com.kesh.web.WebUrlGuard;

@Component
public class WebFetchTool implements Tool {

    private final WebHttpGateway gateway;
    private final HtmlContentExtractor extractor;
    private final KeshProperties properties;

    public WebFetchTool(WebHttpGateway gateway, HtmlContentExtractor extractor, KeshProperties properties) {
        this.gateway = gateway;
        this.extractor = extractor;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "web_fetch";
    }

    @Override
    public String description() {
        return "Fetch one public http(s) page, strip HTML, and return readable text with title, links, and source URL. "
                + "Never use this for localhost or private IPs.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("url", new ToolSchema.Property("string", "Public http or https URL")),
                List.of("url")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.currentTimeMillis();
        if (!properties.getWeb().fetchAvailable()) {
            return ToolResult.fail(name(), "Web fetch is turned off.", System.currentTimeMillis() - started);
        }
        try {
            String raw = ToolArguments.requireString(arguments, "url");
            URI uri = WebUrlGuard.requirePublicHttpUrl(raw);
            WebHttpResponse response = gateway.get(uri);
            if (response.status() == 404) {
                return ToolResult.fail(name(), "Page not found (404): " + uri, System.currentTimeMillis() - started);
            }
            if (response.status() >= 500) {
                return ToolResult.fail(name(), "Remote server error (" + response.status() + "): " + uri, System.currentTimeMillis() - started);
            }
            if (!response.ok()) {
                return ToolResult.fail(name(), "HTTP " + response.status() + " from " + uri, System.currentTimeMillis() - started);
            }
            if (!extractor.acceptableType(response.contentType())) {
                return ToolResult.fail(name(), "Unsupported content type: " + response.contentType(), System.currentTimeMillis() - started);
            }
            WebPage page = extractor.extract(response.bodyText(), WebUrlGuard.normalize(response.url()));
            StringBuilder text = new StringBuilder();
            text.append("url: ").append(page.url()).append('\n');
            text.append("title: ").append(page.title()).append('\n');
            text.append("source: ").append(page.source()).append('\n');
            if (page.description() != null && !page.description().isBlank()) {
                text.append("description: ").append(page.description()).append('\n');
            }
            if (page.publishedDate() != null && !page.publishedDate().isBlank()) {
                text.append("publishedDate: ").append(page.publishedDate()).append('\n');
            }
            text.append("retrievedAt: ").append(page.retrievedAt()).append('\n');
            if (page.links() != null && !page.links().isEmpty()) {
                text.append("links:\n");
                int shown = 0;
                for (String link : page.links()) {
                    text.append("- ").append(link).append('\n');
                    if (++shown >= 12) {
                        break;
                    }
                }
            }
            text.append('\n').append(page.content());
            Map<String, Object> source = new WebCitation(
                    page.title(),
                    page.url(),
                    page.description(),
                    page.source(),
                    page.publishedDate(),
                    page.retrievedAt()
            ).toMap();
            return ToolResult.ok(name(), text.toString(), System.currentTimeMillis() - started, List.of(source));
        } catch (WebFetchException e) {
            return ToolResult.fail(name(), e.getMessage(), System.currentTimeMillis() - started);
        } catch (Exception e) {
            return ToolResult.fail(name(), "Fetch failed: " + e.getMessage(), System.currentTimeMillis() - started);
        }
    }
}
