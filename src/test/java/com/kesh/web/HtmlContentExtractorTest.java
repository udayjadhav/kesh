package com.kesh.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;

class HtmlContentExtractorTest {

    @Test
    void stripsScriptsAndKeepsMainText() {
        HtmlContentExtractor extractor = new HtmlContentExtractor(new KeshProperties());
        WebPage page = extractor.extract(
                """
                <html><head><title>Spring</title>
                <meta name="description" content="Spring site">
                </head>
                <body>
                <nav>ignore me</nav>
                <script>alert(1)</script>
                <article><p>Hello from Spring.</p><a href="/docs">Docs</a></article>
                </body></html>
                """,
                "https://spring.io/"
        );
        assertThat(page.title()).isEqualTo("Spring");
        assertThat(page.description()).isEqualTo("Spring site");
        assertThat(page.content()).contains("Hello from Spring");
        assertThat(page.content()).doesNotContain("alert");
        assertThat(page.content()).doesNotContain("ignore me");
        assertThat(page.links()).anyMatch(link -> link.contains("spring.io"));
    }
}
