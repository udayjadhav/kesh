package com.kesh.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebUrlGuardTest {

    @Test
    void allowsPublicHttpAndHttps() {
        WebUrlGuard.rejectSyntax(WebUrlGuard.parse("https://example.com/article"));
        WebUrlGuard.rejectSyntax(WebUrlGuard.parse("http://example.com"));
    }

    @Test
    void rejectsLocalhostAndLoopback() {
        assertRejected("http://localhost:8080");
        assertRejected("http://127.0.0.1");
        assertRejected("https://127.0.0.1/secret");
        assertRejected("http://0.0.0.0");
    }

    @Test
    void rejectsPrivateAndMetadata() {
        assertRejected("http://10.0.0.5");
        assertRejected("http://192.168.1.20");
        assertRejected("http://172.16.0.2");
        assertRejected("http://169.254.169.254/latest/meta-data");
    }

    @Test
    void rejectsFtp() {
        assertRejected("ftp://example.com/file");
    }

    private static void assertRejected(String url) {
        assertThatThrownBy(() -> WebUrlGuard.requirePublicHttpUrl(url))
                .isInstanceOf(WebFetchException.class);
        assertThat(url).isNotBlank();
    }
}
