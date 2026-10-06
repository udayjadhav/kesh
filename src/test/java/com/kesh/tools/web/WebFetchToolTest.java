package com.kesh.tools.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;
import com.kesh.tools.ToolResult;
import com.kesh.web.HtmlContentExtractor;
import com.kesh.web.MapWebHttpGateway;
import com.kesh.web.WebHttpResponse;

class WebFetchToolTest {

    @Test
    void fetch200ReturnsReadableText() {
        MapWebHttpGateway gateway = new MapWebHttpGateway()
                .putHtml("https://example.com/article", "<html><title>Hello</title><body><article>Useful text</article></body></html>");
        WebFetchTool tool = tool(gateway);
        ToolResult result = tool.execute(Map.of("url", "https://example.com/article"));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Useful text");
        assertThat(result.sources()).isNotEmpty();
    }

    @Test
    void fetch404And500Fail() {
        MapWebHttpGateway gateway = new MapWebHttpGateway()
                .put("https://example.com/missing", 404, "text/html", "nope")
                .put("https://example.com/boom", 500, "text/html", "err");
        WebFetchTool tool = tool(gateway);
        assertThat(tool.execute(Map.of("url", "https://example.com/missing")).success()).isFalse();
        assertThat(tool.execute(Map.of("url", "https://example.com/boom")).error()).contains("500");
    }

    @Test
    void rejectsLocalhostBeforeFetch() {
        MapWebHttpGateway gateway = new MapWebHttpGateway();
        WebFetchTool tool = tool(gateway);
        ToolResult result = tool.execute(Map.of("url", "http://localhost:8080"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).containsIgnoringCase("local");
        assertThat(gateway.requested()).isEmpty();
    }

    @Test
    void rejectsInvalidContentType() {
        MapWebHttpGateway gateway = new MapWebHttpGateway();
        URI uri = URI.create("https://example.com/image");
        gateway.put("https://example.com/image", 200, "image/png", "xxxx");
        WebFetchTool tool = tool(gateway);
        ToolResult result = tool.execute(Map.of("url", "https://example.com/image"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).containsIgnoringCase("content type");
    }

    @Test
    void oversizedAndTimeoutAreFailuresFromGateway() {
        WebFetchTool timeout = new WebFetchTool(
                uri -> {
                    throw new com.kesh.web.WebFetchException("Timed out fetching " + uri);
                },
                new HtmlContentExtractor(new KeshProperties()),
                new KeshProperties()
        );
        assertThat(timeout.execute(Map.of("url", "https://example.com/slow")).error()).contains("Timed out");
        WebFetchTool oversized = new WebFetchTool(
                uri -> {
                    throw new com.kesh.web.WebFetchException("Response exceeded the size limit.");
                },
                new HtmlContentExtractor(new KeshProperties()),
                new KeshProperties()
        );
        assertThat(oversized.execute(Map.of("url", "https://example.com/big")).error()).contains("size limit");
    }

    @Test
    void redirectIsFollowedByGatewayResponseUrl() {
        MapWebHttpGateway gateway = new MapWebHttpGateway() {
            @Override
            public WebHttpResponse get(URI uri) {
                if (uri.toString().equals("https://example.com/old")) {
                    URI dest = URI.create("https://example.com/new");
                    return new WebHttpResponse(
                            200,
                            "text/html",
                            "<html><title>New</title><body>Landed</body></html>".getBytes(StandardCharsets.UTF_8),
                            dest,
                            List.of(uri, dest)
                    );
                }
                return super.get(uri);
            }
        };
        ToolResult result = tool(gateway).execute(Map.of("url", "https://example.com/old"));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Landed");
    }

    private static WebFetchTool tool(MapWebHttpGateway gateway) {
        return new WebFetchTool(gateway, new HtmlContentExtractor(new KeshProperties()), new KeshProperties());
    }
}
