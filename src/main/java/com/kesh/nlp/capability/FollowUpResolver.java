package com.kesh.nlp.capability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves conversational references against the previous turn. Does not authorize tools.
 */
public final class FollowUpResolver {

    private static final Pattern FRESHNESS = Pattern.compile(
            "(?i)\\b(check again|search again|look again|look it up again|refresh|re[- ]?check"
                    + "|search for more|more recent|newer information|still (heavy|true|valid|accurate)"
                    + "|is it still|check if .{0,40}still|again now|latest now)\\b"
    );
    private static final Pattern SUMMARIZE = Pattern.compile(
            "(?i)\\b(summarize|summarise|tldr|tl;dr)\\b"
    );
    private static final Pattern SHORTEN = Pattern.compile(
            "(?i)\\b(make it shorter|shorter|more concise|concise version|brief version)\\b"
    );
    private static final Pattern EXPAND = Pattern.compile(
            "(?i)\\b(elaborate|expand|more detail|go deeper|continue)\\b"
    );
    private static final Pattern REWRITE = Pattern.compile(
            "(?i)\\b(rewrite|rephrase|say that differently)\\b"
    );
    private static final Pattern PROFESSIONAL = Pattern.compile("(?i)\\b(more )?professional\\b");
    private static final Pattern CASUAL = Pattern.compile("(?i)\\b(more )?casual\\b");
    private static final Pattern FRIENDLY = Pattern.compile("(?i)\\b(more )?friendly\\b");
    private static final Pattern SLACK = Pattern.compile("(?i)\\bslack[- ]?(style|friendly|message)?\\b");
    private static final Pattern NATURAL = Pattern.compile("(?i)\\bmore natural|naturalize\\b");
    private static final Pattern FORMAT = Pattern.compile("(?i)\\bformat (this|that|it|the message)\\b");
    private static final Pattern REFERENCE = Pattern.compile(
            "(?i)\\b(above|below|this|that|it|them|these|those"
                    + "|previous (response|answer|message)"
                    + "|last (response|answer|message)"
                    + "|the above|what you said|your (last|previous) (answer|response))\\b"
    );
    private static final Pattern ORDINAL_RESULT = Pattern.compile(
            "(?i)\\b(first|1st|second|2nd|third|3rd)\\s+(result|source|link|point)\\b"
    );

    private FollowUpResolver() {
    }

    public static ClassificationResult maybeApply(
            String userMessage,
            ClassificationResult current,
            DeskContext context
    ) {
        if (userMessage == null || userMessage.isBlank() || context == null || context.awaitingClarification()) {
            return current;
        }
        boolean hasPrevious = hasText(context.previousAssistantMessage()) || hasText(context.previousUserMessage());
        if (!hasPrevious) {
            return current;
        }
        if (isFreshnessAsk(userMessage)) {
            return freshness(userMessage, current, context);
        }
        CapabilityIntent writing = writingOperation(userMessage);
        if (writing == null) {
            return current;
        }
        if (!com.kesh.writing.WritingPayload.payloadFrom(userMessage).isBlank()) {
            return current;
        }
        if (current != null && current.intent() != null
                && current.intent().domain() != CapabilityDomain.WRITING
                && current.intent().domain() != CapabilityDomain.CHAT
                && current.intent().domain() != CapabilityDomain.LLM
                && current.intent().domain() != CapabilityDomain.WEB
                && OpenNlpCapabilityClassifier.tokens(userMessage).length > 8
                && !referencesPrevious(userMessage)) {
            return current;
        }
        Map<String, Object> entities = new LinkedHashMap<>();
        if (current != null) {
            entities.putAll(current.entities());
        }
        String reference = resolveReference(userMessage, context);
        String source = sourceFor(reference, context);
        entities.put("isFollowUp", true);
        entities.put("referencesPreviousContext", true);
        entities.put("reference", reference);
        entities.put("reuseContext", true);
        entities.put("knowledgeRequirement", KnowledgeRequirement.USER_KNOWLEDGE.name());
        if (source != null && !source.isBlank()) {
            entities.put("source", source);
            entities.put("query", source);
        } else if (hasText(context.previousUserMessage())) {
            entities.put("query", context.previousUserMessage().strip());
        }
        return ClassificationResult.single(writing, 0.94, entities, "follow-up");
    }

    public static boolean isFreshnessAsk(String text) {
        return text != null && FRESHNESS.matcher(text).find();
    }

    public static boolean referencesPrevious(String text) {
        return text != null && (REFERENCE.matcher(text).find() || ORDINAL_RESULT.matcher(text).find());
    }

    static CapabilityIntent writingOperation(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (SUMMARIZE.matcher(text).find()) {
            return CapabilityIntent.WRITING_SUMMARIZE;
        }
        if (SHORTEN.matcher(text).find()) {
            return CapabilityIntent.WRITING_SHORTEN;
        }
        if (EXPAND.matcher(text).find() && referencesPrevious(text)) {
            return CapabilityIntent.WRITING_EXPAND;
        }
        if (FORMAT.matcher(text).find()) {
            return CapabilityIntent.WRITING_FORMAT;
        }
        if (SLACK.matcher(text).find() && (text.toLowerCase(Locale.ROOT).contains("make")
                || text.toLowerCase(Locale.ROOT).contains("style")
                || referencesPrevious(text))) {
            return CapabilityIntent.WRITING_DRAFT_SLACK;
        }
        if (PROFESSIONAL.matcher(text).find() && (text.toLowerCase(Locale.ROOT).contains("make")
                || referencesPrevious(text))) {
            return CapabilityIntent.WRITING_PROFESSIONAL;
        }
        if (CASUAL.matcher(text).find() && text.toLowerCase(Locale.ROOT).contains("make")) {
            return CapabilityIntent.WRITING_CASUAL;
        }
        if (FRIENDLY.matcher(text).find() && text.toLowerCase(Locale.ROOT).contains("make")) {
            return CapabilityIntent.WRITING_FRIENDLY;
        }
        if (NATURAL.matcher(text).find()) {
            return CapabilityIntent.WRITING_NATURALIZE;
        }
        if (REWRITE.matcher(text).find()) {
            return CapabilityIntent.WRITING_REWRITE;
        }
        return null;
    }

    static String resolveReference(String text, DeskContext context) {
        Matcher ordinal = ORDINAL_RESULT.matcher(text == null ? "" : text);
        if (ordinal.find()) {
            String rank = ordinal.group(1).toLowerCase(Locale.ROOT);
            int index = switch (rank) {
                case "second", "2nd" -> 1;
                case "third", "3rd" -> 2;
                default -> 0;
            };
            return index == 0 ? "FIRST_RESULT" : index == 1 ? "SECOND_RESULT" : "THIRD_RESULT";
        }
        if (hasText(context.previousAssistantMessage())) {
            return "PREVIOUS_ASSISTANT_RESPONSE";
        }
        return "PREVIOUS_USER_MESSAGE";
    }

    static String sourceFor(String reference, DeskContext context) {
        ConversationState state = context.conversationState();
        if ("FIRST_RESULT".equals(reference) && state != null) {
            return snippet(state.result(0));
        }
        if ("SECOND_RESULT".equals(reference) && state != null) {
            return snippet(state.result(1));
        }
        if ("THIRD_RESULT".equals(reference) && state != null) {
            return snippet(state.result(2));
        }
        if ("PREVIOUS_USER_MESSAGE".equals(reference)) {
            return context.previousUserMessage();
        }
        return context.previousAssistantMessage();
    }

    private static ClassificationResult freshness(
            String userMessage,
            ClassificationResult current,
            DeskContext context
    ) {
        ConversationState state = context.conversationState();
        String query = state != null && hasText(state.lastWebQuery())
                ? state.lastWebQuery()
                : context.previousUserMessage();
        if (!hasText(query)) {
            query = userMessage;
        }
        Map<String, Object> entities = new LinkedHashMap<>();
        if (current != null) {
            entities.putAll(current.entities());
        }
        entities.put("query", query.strip());
        entities.put("isFollowUp", true);
        entities.put("referencesPreviousContext", true);
        entities.put("reference", "PREVIOUS_QUERY");
        entities.put("reuseContext", false);
        entities.put("freshnessOverride", true);
        entities.put("knowledgeRequirement", KnowledgeRequirement.CURRENT_KNOWLEDGE.name());
        return ClassificationResult.single(
                CapabilityIntent.WEB_SEARCH,
                0.93,
                entities,
                "follow-up-freshness"
        );
    }

    private static String snippet(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        Object title = result.get("title");
        Object snippet = result.get("snippet");
        Object url = result.get("url");
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title).append('\n');
        }
        if (snippet != null) {
            sb.append(snippet).append('\n');
        }
        if (url != null) {
            sb.append(url);
        }
        return sb.toString().strip();
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
