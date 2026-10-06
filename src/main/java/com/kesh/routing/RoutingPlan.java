package com.kesh.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ordered capabilities the user appears to need. Not authorization.
 */
public record RoutingPlan(
        List<RoutingStep> steps,
        boolean needsClarification,
        String clarificationQuestion
) {

    public RoutingPlan {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static RoutingPlan empty() {
        return new RoutingPlan(List.of(), false, null);
    }

    public List<Map<String, Object>> toMaps() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RoutingStep step : steps) {
            rows.add(step.toMap());
        }
        return rows;
    }
}
