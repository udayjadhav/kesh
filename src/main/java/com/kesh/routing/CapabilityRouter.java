package com.kesh.routing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.kesh.nlp.RuntimeLexicon;
import com.kesh.nlp.WebLexicon;
import com.kesh.nlp.capability.CapabilityDomain;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.nlp.capability.ClassifiedIntent;

/**
 * Turns a classification into capability steps. Does not authorize.
 */
@Component
public class CapabilityRouter {

    public RoutingPlan plan(String message, ClassificationResult classification) {
        if (classification == null) {
            return RoutingPlan.empty();
        }
        if (classification.needsClarification()) {
            return new RoutingPlan(List.of(), true, classification.clarificationQuestion());
        }
        List<RoutingStep> steps = new ArrayList<>();
        Set<CapabilityIntent> seen = new LinkedHashSet<>();
        for (ClassifiedIntent item : classification.intents()) {
            if (!seen.add(item.intent())) {
                continue;
            }
            steps.add(step(message, item));
        }
        boolean needsSynthesis = steps.stream().anyMatch(step ->
                step.domain() == CapabilityDomain.WEB
                        || step.domain() == CapabilityDomain.RAG
                        || step.domain() == CapabilityDomain.TOOLS
                        || step.domain() == CapabilityDomain.JAVA
                        || step.intent() == CapabilityIntent.RUNTIME_INSPECT
        );
        boolean hasLlm = steps.stream().anyMatch(step -> step.domain() == CapabilityDomain.LLM);
        if (needsSynthesis && !hasLlm) {
            steps.add(new RoutingStep(
                    CapabilityDomain.LLM,
                    CapabilityIntent.LLM_EXPLAIN,
                    message,
                    null,
                    classification.confidence()
            ));
        }
        return new RoutingPlan(steps, false, null);
    }

    private static RoutingStep step(String message, ClassifiedIntent item) {
        Map<String, Object> entities = item.entities();
        String query = string(entities.get("query"));
        if (query == null || query.isBlank()) {
            query = message;
        }
        String target = firstNonBlank(
                string(entities.get("runtime")),
                string(entities.get("target")),
                string(entities.get("app")),
                RuntimeLexicon.findCanonical(message)
        );
        if (item.intent() == CapabilityIntent.WEB_FETCH) {
            String url = WebLexicon.firstUrl(message);
            if (url != null) {
                query = url;
            }
        }
        return new RoutingStep(item.domain(), item.intent(), query, target, item.confidence());
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
