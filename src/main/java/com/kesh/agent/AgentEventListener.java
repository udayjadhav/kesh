package com.kesh.agent;

@FunctionalInterface
public interface AgentEventListener {

    void onEvent(AgentEvent event);

    static AgentEventListener noop() {
        return event -> {
        };
    }
}
