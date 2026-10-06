package com.kesh.nlp;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.routing.RoutingPlan;

public record RequestAnalysis(
        Intent intent,
        double intentConfidence,
        Risk risk,
        double riskConfidence,
        List<String> entities,
        boolean restrictTools,
        Set<String> allowedTools,
        boolean skipModel,
        String skipMessage,
        ClassificationResult classification,
        RoutingPlan plan
) {

    public RequestAnalysis {
        entities = entities == null ? List.of() : List.copyOf(entities);
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
    }

    public RequestAnalysis(
            Intent intent,
            double intentConfidence,
            Risk risk,
            double riskConfidence,
            List<String> entities,
            boolean restrictTools,
            Set<String> allowedTools,
            boolean skipModel,
            String skipMessage
    ) {
        this(
                intent, intentConfidence, risk, riskConfidence, entities,
                restrictTools, allowedTools, skipModel, skipMessage, null, null
        );
    }

    public String hint() {
        StringBuilder sb = new StringBuilder();
        sb.append("Request classifier (local ML, not the LLM policy engine):\n");
        if (classification != null) {
            sb.append("source=").append(classification.source()).append('\n');
            sb.append("domain=").append(classification.domain()).append('\n');
            sb.append("capability=").append(classification.intent()).append('\n');
        }
        sb.append("intent=").append(intent).append(" confidence=")
                .append(String.format(Locale.ROOT, "%.2f", intentConfidence)).append('\n');
        sb.append("risk=").append(risk).append(" confidence=")
                .append(String.format(Locale.ROOT, "%.2f", riskConfidence)).append('\n');
        if (!entities.isEmpty()) {
            sb.append("entities=").append(entities).append('\n');
        }
        if (plan != null && !plan.steps().isEmpty()) {
            sb.append("routing=").append(plan.toMaps()).append('\n');
        }
        sb.append("Answer only the latest user question. Never paste TOOL_RESULT. ");
        sb.append("If this is a short follow-up, keep the previous question in mind. ");
        sb.append("Never write XML tags such as <web_search>. Tool calls must use a <tool_call> JSON block.\n");
        if (!allowedTools.contains("web_search")
                && !allowedTools.contains("web_fetch")
                && !allowedTools.contains("web_crawl")) {
            sb.append("Web tools are not allowed this turn. Do not reuse earlier web results.\n");
        } else {
            sb.append('\n');
        }
        if (restrictTools) {
            if (allowedTools.isEmpty()) {
                sb.append("Do not call tools this turn, including current_time. Answer in plain language.\n");
            } else {
                sb.append("Allowed tools this turn: ").append(String.join(", ", allowedTools)).append('\n');
                if (allowedTools.contains("inspect_runtime")) {
                    sb.append("Decision: check the machine. Call inspect_runtime. ")
                            .append("Never say you cannot access this Mac.\n");
                }
                if (allowedTools.contains("web_search")
                        && !allowedTools.contains("web_fetch")
                        && !allowedTools.contains("web_crawl")) {
                    sb.append("Decision: public web lookup is allowed. Call web_search once, then answer. ")
                            .append("Do not call web_fetch or web_crawl. Do not invent URLs or sources. ")
                            .append("Never say you cannot access weather APIs, the internet, stock prices, or external services. ")
                            .append("Do not answer from local Kesh architecture notes.\n");
                } else if (allowedTools.contains("web_search") || allowedTools.contains("web_fetch") || allowedTools.contains("web_crawl")) {
                    sb.append("Decision: public web lookup is allowed. Call web_search, web_fetch, or web_crawl. ")
                            .append("Do not invent URLs or sources. Quote only tool results. ")
                            .append("Never say you cannot access weather APIs, the internet, stock prices, or external services. ")
                            .append("Do not answer from local Kesh architecture notes.\n");
                }
            }
        }
        if (risk == Risk.DANGEROUS) {
            sb.append("This looks destructive. Wait for user approval. Do not claim success without a tool result.\n");
        }
        return sb.toString();
    }

    public String shortHint() {
        StringBuilder sb = new StringBuilder();
        if (classification != null && classification.intent() != null) {
            sb.append("Type: ").append(classification.intent());
        } else if (intent != null) {
            sb.append("Type: ").append(intent);
        }
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        if (restrictTools && allowedTools.isEmpty()) {
            sb.append("Do not call tools. Use the search notes already in the chat. Do not list sources.\n");
        } else if (restrictTools && !allowedTools.isEmpty()) {
            sb.append("You may call: ").append(String.join(", ", allowedTools)).append(". Then answer. Do not list sources.\n");
        }
        if (classification != null && "STATIC_KNOWLEDGE".equals(String.valueOf(classification.entity("knowledgeRequirement")))) {
            sb.append("This is a static explanation. Do not search the web. Do not emit tool XML.\n");
        }
        Object knowledge = classification == null ? null : classification.entity("knowledgeRequirement");
        if ("CURRENT_KNOWLEDGE".equals(String.valueOf(knowledge))
                || "EXTERNAL_RESEARCH".equals(String.valueOf(knowledge))) {
            sb.append("Live facts need a tool result. Do not invent a count, price, or date. ");
            sb.append("Previous assistant text is not a source.\n");
        }
        if (classification != null && classification.flag("reuseContext")) {
            sb.append("This is a follow-up. Use the previous assistant answer. Do not search again.\n");
        }
        return sb.toString();
    }

    public Map<String, Object> classificationMap() {
        return classification == null ? Map.of() : classification.toMap();
    }
}
