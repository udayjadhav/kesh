package com.kesh.eval;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.capability.CurrentKnowledgeLexicon;
import com.kesh.nlp.capability.KnowledgeRequirement;
import com.kesh.tools.ToolResult;

/**
 * Live public claims must come from a tool result. Previous assistant text is a claim, not a source.
 */
public final class GroundingPolicy {

    private static final Pattern SPECIFIC_QUANTITY = Pattern.compile(
            "\\b\\d{1,3}(?:,\\d{3})+\\b|\\b\\d{4,}\\b"
    );
    private static final String UNVERIFIED = "I couldn't verify a specific number from current sources.";

    private GroundingPolicy() {
    }

    public static boolean requiresVerification(RequestAnalysis analysis) {
        if (analysis == null || analysis.classification() == null) {
            return false;
        }
        Object raw = analysis.classification().entity("knowledgeRequirement");
        KnowledgeRequirement knowledge = KnowledgeRequirement.parse(raw == null ? null : raw.toString());
        return knowledge == KnowledgeRequirement.CURRENT_KNOWLEDGE
                || knowledge == KnowledgeRequirement.EXTERNAL_RESEARCH;
    }

    public static boolean hasVerifiedExternalData(List<ToolResult> tools) {
        if (tools == null) {
            return false;
        }
        for (ToolResult result : tools) {
            if (result != null && result.success() && isExternalTool(result.tool())) {
                return true;
            }
        }
        return false;
    }

    public static boolean claimsUngroundedQuantity(String answer, List<ToolResult> tools) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        Matcher matcher = SPECIFIC_QUANTITY.matcher(answer);
        if (!matcher.find()) {
            return false;
        }
        String evidence = evidenceText(tools);
        matcher.reset();
        while (matcher.find()) {
            if (!evidence.contains(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    public static String enforce(String answer, RequestAnalysis analysis, List<ToolResult> tools) {
        if (answer == null) {
            return "";
        }
        if (!requiresVerification(analysis) && !looksLikeLiveAsk(analysis)) {
            return answer;
        }
        if (hasVerifiedExternalData(tools)) {
            if (claimsUngroundedQuantity(answer, tools)) {
                return UNVERIFIED;
            }
            return answer;
        }
        if (SPECIFIC_QUANTITY.matcher(answer).find()
                || CurrentKnowledgeLexicon.isQuantityAsk(currentAsk(analysis))) {
            return UNVERIFIED;
        }
        return answer;
    }

    public static String unverifiedCurrentAnswer() {
        return UNVERIFIED;
    }

    private static boolean looksLikeLiveAsk(RequestAnalysis analysis) {
        if (analysis == null || analysis.classification() == null) {
            return false;
        }
        Object query = analysis.classification().entity("query");
        return CurrentKnowledgeLexicon.needsLivePublicFacts(query == null ? null : query.toString());
    }

    private static String currentAsk(RequestAnalysis analysis) {
        if (analysis == null || analysis.classification() == null) {
            return "";
        }
        Object query = analysis.classification().entity("query");
        return query == null ? "" : query.toString();
    }

    private static boolean isExternalTool(String tool) {
        return "web_search".equals(tool) || "web_fetch".equals(tool) || "web_crawl".equals(tool);
    }

    private static String evidenceText(List<ToolResult> tools) {
        if (tools == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ToolResult result : tools) {
            if (result == null || !result.success()) {
                continue;
            }
            if (result.output() != null) {
                sb.append(result.output()).append('\n');
            }
            if (result.sources() != null) {
                sb.append(result.sources());
            }
        }
        return sb.toString();
    }
}
