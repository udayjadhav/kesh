package com.kesh.nlp.capability;

import java.util.Locale;
import java.util.regex.Pattern;

import com.kesh.nlp.WebLexicon;

/**
 * Decides whether a request can be answered from model knowledge, company docs, or the public web.
 */
public final class KnowledgeRequirementResolver {

    private static final Pattern COMPANY = Pattern.compile(
            "(?i)\\b(our|company|internal)\\b.{0,40}\\b(architecture|design|doc|document|spec|runbook|confluence)\\b"
                    + "|(?i)\\bwhat does our\\b"
                    + "|(?i)\\baccording to (our|the) (doc|document|architecture|spec)\\b"
    );
    private static final Pattern USER_TEXT = Pattern.compile(
            "(?i)\\b(my message|this message|this email|this slack|this draft|this text)\\b"
    );

    private KnowledgeRequirementResolver() {
    }

    public static KnowledgeRequirement resolve(
            String text,
            ClassificationResult classification,
            DeskContext context
    ) {
        if (text == null || text.isBlank()) {
            return KnowledgeRequirement.UNKNOWN;
        }
        CapabilityIntent intent = classification == null ? null : classification.intent();
        if (intent != null && intent.domain() == CapabilityDomain.WRITING) {
            return KnowledgeRequirement.USER_KNOWLEDGE;
        }
        if (FollowUpResolver.isFreshnessAsk(text)
                && context != null
                && ((context.conversationState() != null && context.conversationState().lastUsedWeb())
                || looksLikeLivePrevious(context))) {
            return KnowledgeRequirement.CURRENT_KNOWLEDGE;
        }
        if ((intent != null && intent.domain() == CapabilityDomain.RAG) || COMPANY.matcher(text).find()) {
            return KnowledgeRequirement.COMPANY_KNOWLEDGE;
        }
        if (WebLexicon.hasExplicitWebCue(text) && looksLikeDirectedSearch(text)) {
            return KnowledgeRequirement.EXTERNAL_RESEARCH;
        }
        if (WebLexicon.isResearchAsk(text)) {
            return KnowledgeRequirement.EXTERNAL_RESEARCH;
        }
        if (WebLexicon.isSearchAsk(text) || WebLexicon.isFetchAsk(text)) {
            return KnowledgeRequirement.CURRENT_KNOWLEDGE;
        }
        if (CurrentKnowledgeLexicon.needsLivePublicFacts(text)) {
            return KnowledgeRequirement.CURRENT_KNOWLEDGE;
        }
        if (USER_TEXT.matcher(text).find()) {
            return KnowledgeRequirement.USER_KNOWLEDGE;
        }
        if (intent != null && intent.domain() == CapabilityDomain.WEB && WebLexicon.isAnyWebAsk(text)) {
            return KnowledgeRequirement.CURRENT_KNOWLEDGE;
        }
        if (intent != null && (intent.domain() == CapabilityDomain.LLM || intent.domain() == CapabilityDomain.CHAT)) {
            return KnowledgeRequirement.STATIC_KNOWLEDGE;
        }
        if (intent != null && intent.domain() == CapabilityDomain.WEB && !WebLexicon.isAnyWebAsk(text)) {
            return KnowledgeRequirement.STATIC_KNOWLEDGE;
        }
        if (context != null && context.conversationState() != null && context.conversationState().lastUsedWeb()
                && FollowUpResolver.isFreshnessAsk(text)) {
            return KnowledgeRequirement.CURRENT_KNOWLEDGE;
        }
        return KnowledgeRequirement.UNKNOWN;
    }

    private static boolean looksLikeLivePrevious(DeskContext context) {
        if (context == null) {
            return false;
        }
        String previous = context.previousUserMessage();
        return previous != null && WebLexicon.isAnyWebAsk(previous);
    }

    static boolean looksLikeDirectedSearch(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("search the web")
                || lower.contains("search online")
                || lower.contains("web search")
                || lower.contains("look up online")
                || lower.contains("on the internet")
                || lower.contains("find online");
    }
}
