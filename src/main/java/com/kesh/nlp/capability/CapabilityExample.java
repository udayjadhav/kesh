package com.kesh.nlp.capability;

import java.util.List;
import java.util.Map;

public record CapabilityExample(
        String text,
        CapabilityIntent intent,
        CapabilityDomain domain,
        Map<String, Object> entities,
        List<CapabilityIntent> extraIntents
) {

    public CapabilityExample {
        extraIntents = extraIntents == null ? List.of() : List.copyOf(extraIntents);
        entities = entities == null ? Map.of() : Map.copyOf(entities);
        domain = domain == null && intent != null ? intent.domain() : domain;
    }
}
