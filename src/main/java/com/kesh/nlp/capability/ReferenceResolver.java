package com.kesh.nlp.capability;

import com.kesh.nlp.DiscourseActs;
import com.kesh.nlp.WebLexicon;

/**
 * Decides whether the current user message continues a previous turn.
 * Default is a new request. History never becomes the current instruction by itself.
 */
public final class ReferenceResolver {

    public record Decision(
            boolean isFollowUp,
            String reference,
            boolean reusePreviousTool,
            boolean requiresFreshData,
            boolean clarification,
            String clarificationMessage
    ) {
        public static Decision independent() {
            return new Decision(false, null, false, false, false, null);
        }

        public static Decision clarify(String message) {
            return new Decision(false, null, false, false, true, message);
        }

        public static Decision followUp(String reference, boolean reusePreviousTool, boolean requiresFreshData) {
            return new Decision(true, reference, reusePreviousTool, requiresFreshData, false, null);
        }
    }

    private ReferenceResolver() {
    }

    public static Decision resolve(String userMessage, ClassificationResult current, DeskContext context) {
        if (userMessage == null || userMessage.isBlank()) {
            return Decision.clarify("I need a bit more to go on. What would you like to know?");
        }
        if (context == null || !hasPrevious(context)) {
            return Decision.independent();
        }
        if (!CurrentKnowledgeLexicon.hasSemanticContent(userMessage)) {
            return Decision.clarify("That does not look like a question. What would you like me to do?");
        }
        if (context.awaitingClarification()) {
            return Decision.followUp("PREVIOUS_USER_MESSAGE", false, false);
        }
        if (DiscourseActs.isConfirmation(userMessage)) {
            return Decision.followUp("PREVIOUS_TOPIC", false, false);
        }
        if (FollowUpResolver.isFreshnessAsk(userMessage)) {
            return Decision.followUp("PREVIOUS_QUERY", false, true);
        }
        if (CurrentKnowledgeLexicon.isSourceVerificationAsk(userMessage)) {
            return Decision.followUp("SOURCE_VERIFICATION", false, true);
        }
        if (FollowUpResolver.writingOperation(userMessage) != null
                && FollowUpResolver.referencesPrevious(userMessage)) {
            return Decision.followUp("PREVIOUS_RESPONSE", true, false);
        }
        if (CurrentKnowledgeLexicon.isQuantityAsk(userMessage) && previousWasCurrentTopic(context)) {
            return Decision.followUp("PREVIOUS_TOPIC", false, true);
        }
        if (liveAttributeContinuation(userMessage, context)) {
            return Decision.followUp("PREVIOUS_TOPIC", false, true);
        }
        if (CurrentKnowledgeLexicon.looksLikeIndependentLookup(userMessage, context.previousUserMessage())) {
            return Decision.independent();
        }
        if (current != null && current.intent() != null
                && current.intent().domain() != CapabilityDomain.CHAT
                && current.intent().domain() != CapabilityDomain.LLM
                && OpenNlpCapabilityClassifier.tokens(userMessage).length >= 3) {
            return Decision.independent();
        }
        return Decision.independent();
    }

    private static boolean liveAttributeContinuation(String userMessage, DeskContext context) {
        if (!CurrentKnowledgeLexicon.isLiveAttributeAsk(userMessage)) {
            return false;
        }
        if (CurrentKnowledgeLexicon.looksLikeIndependentLookup(userMessage, context.previousUserMessage())) {
            return false;
        }
        return WebLexicon.isLiveConditionsAsk(context.previousUserMessage())
                || previousWasCurrentTopic(context);
    }

    private static boolean previousWasCurrentTopic(DeskContext context) {
        ConversationState state = context.conversationState();
        if (state != null && (state.lastUsedWeb() || "CURRENT_KNOWLEDGE".equals(state.lastKnowledge())
                || "EXTERNAL_RESEARCH".equals(state.lastKnowledge()))) {
            return true;
        }
        return WebLexicon.isAnyWebAsk(context.previousUserMessage())
                || CurrentKnowledgeLexicon.needsLivePublicFacts(context.previousUserMessage());
    }

    private static boolean hasPrevious(DeskContext context) {
        return hasText(context.previousUserMessage()) || hasText(context.previousAssistantMessage());
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
