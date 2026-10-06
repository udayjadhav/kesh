package com.kesh.web;

import java.net.URI;
import java.util.List;

public record WebHttpResponse(
        int status,
        String contentType,
        byte[] body,
        URI url,
        List<URI> redirects
) {

    public String bodyText() {
        return body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    public boolean ok() {
        return status >= 200 && status < 300;
    }
}
