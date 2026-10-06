package com.kesh.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

@Component
public class DomainRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final KeshProperties properties;

    public DomainRateLimiter(KeshProperties properties) {
        this.properties = properties;
    }

    public void acquire(String host) {
        acquire(host, Duration.ofSeconds(65));
    }

    public void acquire(String host, Duration waitBudget) {
        String key = host == null ? "" : host.toLowerCase();
        int limit = Math.max(1, properties.getWeb().getRateLimit().getRequestsPerDomainPerMinute());
        Instant deadline = Instant.now().plus(waitBudget == null ? Duration.ZERO : waitBudget);
        while (true) {
            synchronized (lockFor(key)) {
                Deque<Instant> times = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
                Instant cutoff = Instant.now().minus(Duration.ofMinutes(1));
                while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                    times.removeFirst();
                }
                if (times.size() < limit) {
                    times.addLast(Instant.now());
                    return;
                }
                Instant oldest = times.peekFirst();
                long sleepMs = oldest == null
                        ? 50
                        : Math.max(10, Duration.between(Instant.now(), oldest.plus(Duration.ofMinutes(1))).toMillis());
                if (Instant.now().plusMillis(sleepMs).isAfter(deadline)) {
                    throw new WebFetchException("Rate limit for " + key + " (" + limit + " requests per minute).");
                }
                try {
                    Thread.sleep(Math.min(sleepMs, 250));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new WebFetchException("Rate limit wait interrupted.", e);
                }
            }
        }
    }

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private Object lockFor(String host) {
        return locks.computeIfAbsent(host, ignored -> new Object());
    }
}
