package com.kesh.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

@Component
public class WebCache {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final KeshProperties properties;

    public WebCache(KeshProperties properties) {
        this.properties = properties;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        if (!properties.getWeb().getCache().isEnabled() || key == null) {
            return Optional.empty();
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        if (!type.isInstance(entry.value())) {
            return Optional.empty();
        }
        return Optional.of(type.cast(entry.value()));
    }

    public void put(String key, Object value) {
        if (!properties.getWeb().getCache().isEnabled() || key == null || value == null) {
            return;
        }
        Duration ttl = Duration.ofMinutes(Math.max(1, properties.getWeb().getCache().getTtlMinutes()));
        entries.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    public void clear() {
        entries.clear();
    }

    private record Entry(Object value, Instant expiresAt) {
    }
}
