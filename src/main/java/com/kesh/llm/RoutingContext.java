package com.kesh.llm;

/**
 * Per-request routing record. Not a policy engine.
 */
public final class RoutingContext {

    private static final ThreadLocal<RoutingDecision> CURRENT = new ThreadLocal<>();

    private RoutingContext() {
    }

    public static void set(RoutingDecision decision) {
        CURRENT.set(decision);
    }

    public static RoutingDecision get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
