package com.kesh.web;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Blocks Gemma from using web tools as a local-network proxy (SSRF).
 */
public final class WebUrlGuard {

    private WebUrlGuard() {
    }

    public static URI requirePublicHttpUrl(String raw) {
        URI uri = parse(raw);
        rejectSyntax(uri);
        rejectResolved(uri);
        return uri;
    }

    public static URI parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new WebFetchException("URL is required.");
        }
        String trimmed = raw.trim();
        int space = trimmed.indexOf(' ');
        if (space > 0) {
            trimmed = trimmed.substring(0, space);
        }
        if (trimmed.endsWith(")") || trimmed.endsWith("]") || trimmed.endsWith(">") || trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new WebFetchException("Invalid URL.", e);
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new WebFetchException("URL must include http or https and a host.");
        }
        return uri.normalize();
    }

    public static void rejectSyntax(URI uri) {
        if (uri == null) {
            throw new WebFetchException("URL is required.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new WebFetchException("Only http and https URLs are allowed.");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new WebFetchException("URLs with credentials are not allowed.");
        }
        String host = host(uri);
        if (host.isBlank()) {
            throw new WebFetchException("URL host is missing.");
        }
        if (isLocalHostName(host) || isBlockedIpLiteral(host)) {
            throw new WebFetchException("Local and private addresses are not allowed: " + host);
        }
    }

    public static void rejectResolved(URI uri) {
        String host = host(uri);
        if (isIpLiteral(host)) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new WebFetchException("Could not resolve host: " + host, e);
        }
        if (addresses == null || addresses.length == 0) {
            throw new WebFetchException("Could not resolve host: " + host);
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new WebFetchException("Host resolves to a private or local address: " + host);
            }
        }
    }

    public static String host(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return "";
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return host.toLowerCase(Locale.ROOT).trim();
    }

    public static String registrableHost(URI uri) {
        String host = host(uri);
        if (host.startsWith("www.")) {
            return host.substring(4);
        }
        return host;
    }

    public static boolean sameDomain(URI left, URI right) {
        return registrableHost(left).equals(registrableHost(right));
    }

    public static String normalize(URI uri) {
        URI safe = uri.normalize();
        String path = safe.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String query = safe.getQuery() == null ? "" : "?" + safe.getQuery();
        return safe.getScheme().toLowerCase(Locale.ROOT) + "://" + host(safe) + path + query;
    }

    private static boolean isLocalHostName(String host) {
        return "localhost".equals(host)
                || "localhost.localdomain".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || "metadata.google.internal".equals(host);
    }

    private static boolean isIpLiteral(String host) {
        return host.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.')
                || host.contains(":");
    }

    private static boolean isBlockedIpLiteral(String host) {
        if (!isIpLiteral(host)) {
            return false;
        }
        try {
            return isBlockedAddress(InetAddress.getByName(host));
        } catch (UnknownHostException e) {
            return true;
        }
    }

    static boolean isBlockedAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            if (a == 0 || a == 10 || a == 127) {
                return true;
            }
            if (a == 169 && b == 254) {
                return true;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            if (a == 100 && b >= 64 && b <= 127) {
                return true;
            }
        }
        return false;
    }
}
