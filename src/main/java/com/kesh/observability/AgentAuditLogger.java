package com.kesh.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgentAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditLogger.class);
    private static final Set<String> SECRET_KEYS = Set.of(
            "token", "accesstoken", "access_token", "refreshtoken", "password", "secret",
            "authorization", "api_key", "apikey", "cookie", "client_secret"
    );
    private static final int MAX_RESULT = 500;

    public void log(AuditEvent event) {
        log.info(
                "kesh.audit ts={} conversationId={} model={} tool={} permission={} approval={} durationMs={} args={} result={} error={} userMessage={}",
                event.timestamp(),
                event.conversationId(),
                event.model(),
                event.tool(),
                event.permissionLevel(),
                event.approvalStatus(),
                event.executionDurationMs(),
                redact(event.arguments()),
                truncate(event.toolResult()),
                event.error(),
                truncate(event.userMessage())
        );
    }

    static Map<String, Object> redact(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        arguments.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            boolean secret = SECRET_KEYS.stream().anyMatch(s -> normalized.contains(s.replace("_", "")));
            copy.put(key, secret ? "[redacted]" : value);
        });
        return copy;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= MAX_RESULT) {
            return compact;
        }
        return compact.substring(0, MAX_RESULT) + "...";
    }
}
