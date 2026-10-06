package com.kesh.web;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

@Component
public class DefaultWebHttpGateway implements WebHttpGateway {

    private final HttpClient httpClient;
    private final KeshProperties properties;
    private final WebCache cache;
    private final DomainRateLimiter rateLimiter;

    public DefaultWebHttpGateway(
            @Qualifier("webRedirectSafeHttpClient") HttpClient httpClient,
            KeshProperties properties,
            WebCache cache,
            DomainRateLimiter rateLimiter
    ) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public WebHttpResponse get(URI uri) {
        return get(uri, Map.of());
    }

    @Override
    public WebHttpResponse get(URI uri, Map<String, String> headers) {
        URI start = WebUrlGuard.requirePublicHttpUrl(uri.toString());
        String cacheKey = "GET:" + WebUrlGuard.normalize(start) + (headers == null || headers.isEmpty() ? "" : headers.toString());
        Optional<WebHttpResponse> cached = cache.get(cacheKey, WebHttpResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<URI> hops = new ArrayList<>();
        URI current = start;
        int maxRedirects = Math.max(1, properties.getWeb().getFetch().getMaxRedirects());
        for (int i = 0; i <= maxRedirects; i++) {
            WebUrlGuard.requirePublicHttpUrl(current.toString());
            rateLimiter.acquire(WebUrlGuard.host(current));
            hops.add(current);
            Raw raw = send(current, headers);
            if (raw.status() >= 300 && raw.status() < 400) {
                if (raw.location() == null || raw.location().isBlank()) {
                    throw new WebFetchException("Redirect without Location from " + current);
                }
                current = current.resolve(raw.location().trim());
                continue;
            }
            WebHttpResponse response = new WebHttpResponse(
                    raw.status(),
                    raw.contentType(),
                    raw.body(),
                    current,
                    List.copyOf(hops)
            );
            cache.put(cacheKey, response);
            return response;
        }
        throw new WebFetchException("Too many redirects.");
    }

    private Raw send(URI uri, Map<String, String> headers) {
        int timeout = Math.max(2, properties.getWeb().getFetch().getTimeoutSeconds());
        int maxBytes = properties.getWeb().getFetch().maxResponseBytes();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(timeout))
                .header("User-Agent", properties.getWeb().getUserAgent())
                .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json;q=0.9,*/*;q=0.1")
                .GET();
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name != null && value != null && !name.equalsIgnoreCase("host")) {
                    builder.header(name, value);
                }
            });
        }
        HttpRequest request = builder.build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            String location = response.headers().firstValue("location").orElse(null);
            long declared = response.headers().firstValue("content-length")
                    .map(value -> {
                        try {
                            return Long.parseLong(value.trim());
                        } catch (NumberFormatException e) {
                            return -1L;
                        }
                    })
                    .orElse(-1L);
            if (declared > maxBytes) {
                try (InputStream ignored = response.body()) {
                    // close
                }
                throw new WebFetchException("Response is larger than the "
                        + properties.getWeb().getFetch().getMaxResponseSizeMb() + " MB limit.");
            }
            byte[] body = readLimited(response.body(), maxBytes);
            return new Raw(response.statusCode(), contentType, location, body);
        } catch (WebFetchException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebFetchException("Request interrupted.", e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new WebFetchException("Timed out fetching " + uri, e);
        } catch (Exception e) {
            throw new WebFetchException("Fetch failed: " + e.getMessage(), e);
        }
    }

    private static byte[] readLimited(InputStream stream, int maxBytes) {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new WebFetchException("Response exceeded the size limit.");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (WebFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new WebFetchException("Could not read response body.", e);
        }
    }

    private record Raw(int status, String contentType, String location, byte[] body) {
    }
}
