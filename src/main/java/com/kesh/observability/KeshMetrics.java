package com.kesh.observability;

import org.springframework.stereotype.Component;

import com.kesh.nlp.RequestAnalysis;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Local counters only. Never logs the user message.
 */
@Component
public class KeshMetrics {

    private final MeterRegistry registry;

    public KeshMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTurn(RequestAnalysis analysis, long latencyMs, boolean skipModel) {
        if (registry == null) {
            return;
        }
        String intent = analysis == null || analysis.intent() == null ? "unknown" : analysis.intent().name();
        String domain = analysis == null || analysis.classification() == null
                ? "none"
                : analysis.classification().domain().name();
        registry.counter("kesh.turns", "intent", intent, "domain", domain, "skip", String.valueOf(skipModel))
                .increment();
        Timer.builder("kesh.turn.latency")
                .tag("intent", intent)
                .register(registry)
                .record(java.time.Duration.ofMillis(Math.max(0, latencyMs)));
    }
}
