package com.kesh.web;

public class WebFetchException extends RuntimeException {

    public WebFetchException(String message) {
        super(message);
    }

    public WebFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
