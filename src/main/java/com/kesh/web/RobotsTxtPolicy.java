package com.kesh.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class RobotsTxtPolicy {

    public boolean allows(String robotsTxt, String userAgent, URI url) {
        if (robotsTxt == null || robotsTxt.isBlank()) {
            return true;
        }
        String path = url.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String agent = userAgent == null ? "*" : userAgent.toLowerCase(Locale.ROOT);
        List<Rule> rules = new ArrayList<>();
        boolean inGroup = false;
        for (String rawLine : robotsTxt.split("\n")) {
            String line = rawLine.strip();
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment).strip();
            }
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if ("user-agent".equals(key)) {
                String name = value.toLowerCase(Locale.ROOT);
                inGroup = "*".equals(name) || agent.contains(name) || name.contains("kesh");
                continue;
            }
            if (!inGroup) {
                continue;
            }
            if ("disallow".equals(key) || "allow".equals(key)) {
                rules.add(new Rule("allow".equals(key), value));
            }
        }
        int best = -1;
        boolean allowed = true;
        for (Rule rule : rules) {
            if (rule.prefix.isBlank()) {
                continue;
            }
            if (!path.startsWith(rule.prefix)) {
                continue;
            }
            int len = rule.prefix.length();
            if (len > best || (len == best && rule.allow)) {
                best = len;
                allowed = rule.allow;
            }
        }
        return allowed;
    }

    private record Rule(boolean allow, String prefix) {
    }
}
