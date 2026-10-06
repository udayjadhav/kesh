package com.kesh.web;

import java.net.URI;
import java.util.Map;

public interface WebHttpGateway {

    WebHttpResponse get(URI uri);

    default WebHttpResponse get(URI uri, Map<String, String> headers) {
        return get(uri);
    }
}
