package com.kesh.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;

class BoundedWebCrawlerTest {

    @Test
    void respectsMaxPagesDepthSameDomainDuplicatesAndRobots() {
        KeshProperties properties = new KeshProperties();
        properties.getWeb().getCrawl().setMaxPages(10);
        properties.getWeb().getCrawl().setMaxDepth(2);
        properties.getWeb().getCrawl().setSameDomainOnly(true);
        MapWebHttpGateway gateway = new MapWebHttpGateway()
                .put("https://example.com/robots.txt", 200, "text/plain", """
                        User-agent: *
                        Disallow: /private
                        """)
                .putHtml("https://example.com/", """
                        <html><title>Home</title><body>
                        <a href="https://example.com/a">A</a>
                        <a href="https://example.com/a">A again</a>
                        <a href="https://example.com/b">B</a>
                        <a href="https://example.com/private">secret</a>
                        <a href="https://other.example.net/x">away</a>
                        </body></html>
                        """)
                .putHtml("https://example.com/a", "<html><title>A</title><body>Page A <a href='https://example.com/cycle'>cycle</a></body></html>")
                .putHtml("https://example.com/b", "<html><title>B</title><body>Page B</body></html>")
                .putHtml("https://example.com/private", "<html><title>No</title><body>secret</body></html>")
                .putHtml("https://example.com/cycle", "<html><title>Cycle</title><body><a href='https://example.com/'>home</a></body></html>")
                .putHtml("https://other.example.net/x", "<html><title>Away</title><body>nope</body></html>");
        BoundedWebCrawler crawler = new BoundedWebCrawler(
                gateway,
                new HtmlContentExtractor(properties),
                new RobotsTxtPolicy(),
                properties
        );
        List<WebPage> pages = crawler.crawl("https://example.com/", 2, 1);
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).url()).contains("example.com");
        assertThat(pages.stream().map(WebPage::title)).doesNotContain("No", "Away");
        assertThat(gateway.requested()).extracting(URI::toString)
                .noneMatch(url -> url.contains("other.example.net"));
    }

    @Test
    void robotsPolicyBlocksDisallowedPath() {
        RobotsTxtPolicy policy = new RobotsTxtPolicy();
        String robots = "User-agent: *\nDisallow: /hidden\nAllow: /\n";
        assertThat(policy.allows(robots, "Kesh", URI.create("https://example.com/hidden"))).isFalse();
        assertThat(policy.allows(robots, "Kesh", URI.create("https://example.com/docs"))).isTrue();
    }
}
