package com.kesh.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.capability.CapabilityIntent;

/**
 * Picks a local chat model by role. Policy does not live here.
 */
@Component
public class ModelRouter {

    private final KeshProperties properties;
    private final ModelCatalog catalog;

    public ModelRouter(KeshProperties properties) {
        this(properties, null);
    }

    @Autowired
    public ModelRouter(KeshProperties properties, ModelCatalog catalog) {
        this.properties = properties;
        this.catalog = catalog;
    }

    public String modelFor(RequestAnalysis analysis) {
        return decide(analysis, null).model();
    }

    public String judgeModel() {
        return decideRole(ModelRole.JUDGE_MODEL, RequestComplexity.LOW, "judge").model();
    }

    public RoutingDecision decide(RequestAnalysis analysis, String userMessage) {
        RequestComplexity complexity = RequestComplexity.of(analysis, userMessage);
        ModelRole role = roleFor(analysis, complexity);
        RoutingDecision decision = decideRole(role, complexity, reason(analysis, role, complexity));
        RoutingContext.set(decision);
        return decision;
    }

    public RoutingDecision fallback(RoutingDecision previous) {
        if (catalog == null) {
            return previous;
        }
        RegisteredModel failed = previous == null ? null : catalog.find(previous.model());
        RegisteredModel next = catalog.fallback(failed);
        if (next == null || previous != null && next.matches(previous.model())) {
            return previous;
        }
        RoutingDecision decision = new RoutingDecision(
                next.model(),
                next.provider(),
                next.baseUrl(),
                previous == null ? ModelRole.FAST_MODEL : previous.role(),
                previous == null ? RequestComplexity.LOW : previous.complexity(),
                true,
                previous == null ? "" : previous.model(),
                "fallback after failure"
        );
        RoutingContext.set(decision);
        return decision;
    }

    public boolean shouldRefine(RequestAnalysis analysis, String userMessage) {
        if (!properties.getModelRouter().isEnabled() || !properties.getModelRouter().isRefineEnabled()) {
            return false;
        }
        RequestComplexity complexity = RequestComplexity.of(analysis, userMessage);
        if (complexity != RequestComplexity.HIGH) {
            return false;
        }
        RoutingDecision primary = decide(analysis, userMessage);
        RoutingDecision refiner = decideRole(ModelRole.REFINER_MODEL, complexity, "refine");
        return refiner != null && !refiner.model().isBlank() && !refiner.model().equals(primary.model());
    }

    private RoutingDecision decideRole(ModelRole role, RequestComplexity complexity, String reason) {
        String fallback = properties.getLlm().getModel();
        String provider = properties.getLlm().getProvider();
        String baseUrl = properties.getLlm().getBaseUrl();
        if (!properties.getModelRouter().isEnabled()) {
            return new RoutingDecision(fallback, provider, baseUrl, role, complexity, false, "", "router disabled");
        }
        String pinned = pinned(role);
        if (pinned != null) {
            RegisteredModel listed = catalog == null ? null : catalog.find(pinned);
            return new RoutingDecision(
                    pinned,
                    listed == null ? provider : listed.provider(),
                    listed == null ? baseUrl : listed.baseUrl(),
                    role,
                    complexity,
                    false,
                    "",
                    reason + " (pinned)"
            );
        }
        if (catalog != null) {
            RegisteredModel chosen = catalog.forRole(role);
            if (chosen != null) {
                return new RoutingDecision(
                        chosen.model(),
                        chosen.provider(),
                        chosen.baseUrl(),
                        role,
                        complexity,
                        false,
                        "",
                        reason
                );
            }
        }
        return new RoutingDecision(fallback, provider, baseUrl, role, complexity, false, "", reason + " (default)");
    }

    private String pinned(ModelRole role) {
        KeshProperties.ModelRouterSettings settings = properties.getModelRouter();
        return switch (role) {
            case FAST_MODEL -> firstNonBlank(settings.getChatModel());
            case CODE_MODEL, TOOL_PLANNER_MODEL -> firstNonBlank(settings.getCodeModel());
            case JUDGE_MODEL -> firstNonBlank(settings.getJudgeModel());
            case REASONING_MODEL -> firstNonBlank(settings.getReasoningModel());
            case REFINER_MODEL -> firstNonBlank(settings.getRefinerModel());
            case WRITER_MODEL -> firstNonBlank(settings.getWriterModel(), settings.getChatModel());
        };
    }

    static ModelRole roleFor(RequestAnalysis analysis, RequestComplexity complexity) {
        CapabilityIntent intent = analysis == null || analysis.classification() == null
                ? null
                : analysis.classification().intent();
        if (intent != null && intent.domain() == com.kesh.nlp.capability.CapabilityDomain.WRITING) {
            if (intent == CapabilityIntent.WRITING_DRAFT_EMAIL
                    || intent == CapabilityIntent.WRITING_DRAFT_PR
                    || intent == CapabilityIntent.WRITING_DRAFT_CONFLUENCE
                    || intent == CapabilityIntent.WRITING_DIPLOMATIC
                    || intent == CapabilityIntent.WRITING_EXPAND
                    || complexity == RequestComplexity.HIGH) {
                return ModelRole.REASONING_MODEL;
            }
            return ModelRole.WRITER_MODEL;
        }
        if (intent == CapabilityIntent.LLM_CODE_HELP || intent == CapabilityIntent.JAVA_SNIPPET_EXECUTION) {
            return ModelRole.CODE_MODEL;
        }
        if (intent == CapabilityIntent.LLM_DESIGN
                || intent == CapabilityIntent.LLM_COMPARE
                || intent == CapabilityIntent.WEB_RESEARCH
                || intent == CapabilityIntent.RAG_DOCUMENT_RESEARCH
                || complexity == RequestComplexity.HIGH) {
            return ModelRole.REASONING_MODEL;
        }
        if (intent == CapabilityIntent.WEB_CRAWL
                || (analysis != null && analysis.allowedTools() != null && analysis.allowedTools().size() >= 3
                && intent != CapabilityIntent.CHAT_GENERAL)) {
            return ModelRole.TOOL_PLANNER_MODEL;
        }
        return ModelRole.FAST_MODEL;
    }

    private static String reason(RequestAnalysis analysis, ModelRole role, RequestComplexity complexity) {
        CapabilityIntent intent = analysis == null || analysis.classification() == null
                ? null
                : analysis.classification().intent();
        return (intent == null ? "CHAT" : intent.name()) + " / " + complexity + " -> " + role;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }
}
