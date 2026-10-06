package com.kesh.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.nlp.capability.CapabilityClassifier;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.nlp.capability.CurrentKnowledgeLexicon;
import com.kesh.nlp.capability.DeskContext;
import com.kesh.nlp.capability.FollowUpResolver;
import com.kesh.nlp.capability.KnowledgeRequirement;
import com.kesh.nlp.capability.KnowledgeRequirementResolver;
import com.kesh.nlp.capability.OpenNlpCapabilityClassifier;
import com.kesh.nlp.capability.ReferenceResolver;
import com.kesh.routing.CapabilityRouter;
import com.kesh.routing.RoutingPlan;
import com.kesh.tools.Tool;
import com.kesh.workspace.WorkspaceMode;

@Component
public class RequestAnalyzer {

    private static final java.util.regex.Pattern CLOCK_QUESTION = java.util.regex.Pattern.compile(
            "(?i)\\b(?:what(?:['’]?s| is)(?: the)?(?: current)? time|what time is it|tell me(?: the)?(?: current)? time|give me(?: the)?(?: current)? time|current time(?: please)?|time is it now|time now|now time|what(?:['’]?s| is)(?: the)? date|today['’]?s date|what day is it)\\b"
    );

    private final CapabilityClassifier capabilityClassifier;
    private final RiskClassifier riskClassifier;
    private final EntityExtractor entityExtractor;
    private final NlpDecisionEngine decisionEngine;
    private final CapabilityRouter capabilityRouter;
    private final KeshProperties properties;

    public RequestAnalyzer(
            CapabilityClassifier capabilityClassifier,
            RiskClassifier riskClassifier,
            EntityExtractor entityExtractor,
            NlpDecisionEngine decisionEngine,
            CapabilityRouter capabilityRouter,
            KeshProperties properties
    ) {
        this.capabilityClassifier = capabilityClassifier;
        this.riskClassifier = riskClassifier;
        this.entityExtractor = entityExtractor;
        this.decisionEngine = decisionEngine;
        this.capabilityRouter = capabilityRouter;
        this.properties = properties;
    }

    public RequestAnalysis analyze(String userMessage, List<Tool> catalog) {
        return analyze(null, userMessage, catalog);
    }

    public RequestAnalysis analyze(WorkspaceMode mode, String userMessage, List<Tool> catalog) {
        return analyze(mode, userMessage, catalog, DeskContext.of(mode));
    }

    public RequestAnalysis analyze(WorkspaceMode mode, String userMessage, List<Tool> catalog, DeskContext deskContext) {
        if (!properties.getNlp().isEnabled()) {
            return new RequestAnalysis(
                    Intent.CHAT, 1.0, Risk.READ, 1.0, List.of(),
                    false, Set.of(), false, null
            );
        }
        DeskContext context = deskContext == null ? DeskContext.of(mode) : deskContext;
        String classifyText = userMessage;
        if (context.awaitingClarification()
                && context.previousUserMessage() != null
                && !context.previousUserMessage().isBlank()) {
            classifyText = context.previousUserMessage() + "\nFollow-up: " + userMessage;
        }
        ClassificationResult classification = capabilityClassifier.classify(classifyText, context);
        ReferenceResolver.Decision reference = ReferenceResolver.resolve(userMessage, classification, context);
        if (reference.clarification()) {
            java.util.Map<String, Object> entities = new java.util.LinkedHashMap<>();
            entities.put("query", userMessage);
            entities.put("isFollowUp", false);
            entities.put("referencesPreviousContext", false);
            classification = ClassificationResult.single(
                    CapabilityIntent.CHAT_GENERAL,
                    1.0,
                    entities,
                    "ambiguous-request"
            ).asClarification(reference.clarificationMessage());
        } else if (context.awaitingClarification()
                && context.previousUserMessage() != null
                && !context.previousUserMessage().isBlank()) {
            classification = applyFollowUp(userMessage, classification, context);
        } else {
            ClassificationResult followUp = FollowUpResolver.maybeApply(userMessage, classification, context);
            if (followUp != null && ("follow-up".equals(followUp.source())
                    || "follow-up-freshness".equals(followUp.source()))) {
                classification = followUp;
            } else if (reference.isFollowUp() && "SOURCE_VERIFICATION".equals(reference.reference())) {
                classification = currentTopicRetrieval(userMessage, classification, context, "SOURCE_VERIFICATION");
            } else if (reference.isFollowUp() && "PREVIOUS_TOPIC".equals(reference.reference())
                    && (CurrentKnowledgeLexicon.isQuantityAsk(userMessage)
                    || CurrentKnowledgeLexicon.isLiveAttributeAsk(userMessage))) {
                classification = currentTopicRetrieval(userMessage, classification, context, "PREVIOUS_TOPIC");
            } else if (reference.isFollowUp() && DiscourseActs.isConfirmation(userMessage)) {
                classification = continuePreviousTurn(userMessage, classification, context);
            } else if (reference.isFollowUp() && FollowUpResolver.isFreshnessAsk(userMessage)) {
                classification = continuePreviousTurn(userMessage, classification, context);
            }
        }
        KnowledgeRequirement knowledge = KnowledgeRequirementResolver.resolve(userMessage, classification, context);
        if (isWebCapability(classification)
                && knowledge == KnowledgeRequirement.STATIC_KNOWLEDGE
                && !WebLexicon.isAnyWebAsk(userMessage)
                && !CurrentKnowledgeLexicon.needsLivePublicFacts(userMessage)
                && !classification.flag("freshnessOverride")
                && isStaticExplainAsk(userMessage)
                && !"turn-continuation".equals(classification.source())
                && !"short-lookup".equals(classification.source())
                && !"follow-up-freshness".equals(classification.source())
                && !"clarification-reply".equals(classification.source())
                && !"current-topic-retrieval".equals(classification.source())) {
            java.util.Map<String, Object> entities = new java.util.LinkedHashMap<>(classification.entities());
            entities.put("query", userMessage);
            classification = ClassificationResult.single(
                    CapabilityIntent.LLM_EXPLAIN,
                    Math.max(classification.confidence(), 0.86),
                    entities,
                    "static-knowledge"
            );
            knowledge = KnowledgeRequirement.STATIC_KNOWLEDGE;
        }
        if (shouldForceCurrentWeb(userMessage, classification, knowledge, reference)) {
            java.util.Map<String, Object> entities = new java.util.LinkedHashMap<>(classification.entities());
            String query = stringEntity(classification, "query", userMessage);
            if (!reference.isFollowUp()) {
                query = userMessage;
            }
            entities.put("query", query);
            entities.put("knowledgeRequirement", knowledge == KnowledgeRequirement.EXTERNAL_RESEARCH
                    ? KnowledgeRequirement.EXTERNAL_RESEARCH.name()
                    : KnowledgeRequirement.CURRENT_KNOWLEDGE.name());
            if (CurrentKnowledgeLexicon.isMarketQuoteAsk(userMessage)) {
                entities.put("requestClass", "MARKET_DATA");
            } else if (WebLexicon.isLiveConditionsAsk(userMessage) || CurrentKnowledgeLexicon.isLiveAttributeAsk(userMessage)) {
                entities.put("requestClass", "LIVE_CONDITIONS");
            } else {
                entities.put("requestClass", "CURRENT_INFORMATION");
            }
            classification = ClassificationResult.single(
                    knowledge == KnowledgeRequirement.EXTERNAL_RESEARCH
                            ? CapabilityIntent.WEB_RESEARCH
                            : CapabilityIntent.WEB_SEARCH,
                    Math.max(classification.confidence(), 0.88),
                    entities,
                    "current-knowledge"
            );
            if (knowledge != KnowledgeRequirement.EXTERNAL_RESEARCH) {
                knowledge = KnowledgeRequirement.CURRENT_KNOWLEDGE;
            }
        }
        classification = classification.withEntities(signalEntities(userMessage, classification, knowledge, context));
        if ((classification.intent() == CapabilityIntent.WEB_FETCH
                || classification.intent() == CapabilityIntent.WEB_CRAWL)
                && WebLexicon.firstUrl(userMessage) == null) {
            classification = ClassificationResult.single(
                    CapabilityIntent.WEB_SEARCH,
                    Math.max(classification.confidence(), 0.85),
                    OpenNlpCapabilityClassifier.entitiesFor(userMessage, CapabilityIntent.WEB_SEARCH),
                    "fetch-without-url"
            );
        }
        if (CommandLexicon.isImperativeShellAsk(userMessage)) {
            classification = ClassificationResult.single(
                    CapabilityIntent.TERMINAL_EXECUTION,
                    Math.max(classification.confidence(), 0.9),
                    classification.entities(),
                    "imperative-shell"
            );
        }
        String planMessage = stringEntity(classification, "query", userMessage);
        RoutingPlan plan = capabilityRouter.plan(planMessage, classification);
        Intent intent = classification.intent().legacyIntent();
        double intentConfidence = classification.confidence();
        Risk risk = riskClassifier.classify(userMessage, intent);
        double riskConfidence = riskClassifier.confidence(userMessage);
        if (risk == Risk.DANGEROUS && intent != Intent.TERMINAL && intent != Intent.JAVA_SNIPPET
                && intent != Intent.WRITING) {
            intent = Intent.DESTRUCTIVE;
        }
        List<String> entities = mergeEntities(entityExtractor.extract(userMessage), classification.entities());
        return decisionEngine.decide(
                mode,
                userMessage,
                catalog,
                intent,
                intentConfidence,
                risk,
                riskConfidence,
                entities,
                classification,
                plan
        );
    }

    public java.util.Map<String, Object> toClientMap(RequestAnalysis analysis) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("intent", analysis.intent().name());
        map.put("intentConfidence", round(analysis.intentConfidence()));
        map.put("risk", analysis.risk().name());
        map.put("riskConfidence", round(analysis.riskConfidence()));
        map.put("entities", analysis.entities());
        map.put("allowedTools", List.copyOf(analysis.allowedTools()));
        map.put("skipModel", analysis.skipModel());
        map.put("allowed", !analysis.skipModel());
        map.put("tool", preferredTool(analysis));
        if (analysis.skipMessage() != null) {
            map.put("reason", analysis.skipMessage());
        }
        map.put("decision", decisionLabel(analysis.intent()));
        if (analysis.classification() != null) {
            map.put("classification", analysis.classification().toMap());
            map.put("domain", analysis.classification().domain().name());
            map.put("capability", analysis.classification().intent().name());
            map.put("classifierSource", analysis.classification().source());
        }
        if (analysis.plan() != null) {
            map.put("routing", analysis.plan().toMaps());
        }
        if (analysis.classification() != null) {
            map.put("knowledge", knowledgeLabel(analysis));
            map.put("followUp", analysis.classification().flag("isFollowUp"));
            map.put("referencesPreviousContext", analysis.classification().flag("referencesPreviousContext"));
            Object reference = analysis.classification().entity("reference");
            if (reference != null) {
                map.put("reference", reference.toString());
            }
        }
        return map;
    }

    public static boolean isRuntimeQuestion(String text) {
        return RuntimeLexicon.isVersionAsk(text);
    }

    public static String inferRuntimeName(String text) {
        return RuntimeLexicon.findCanonical(text);
    }

    public static boolean isClockQuestion(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT).strip();
        if (lower.startsWith("why") || lower.startsWith("how come") || lower.contains("why did")
                || lower.contains("why are you") || lower.contains("why do you")) {
            return false;
        }
        return CLOCK_QUESTION.matcher(lower).find();
    }

    private static String preferredTool(RequestAnalysis analysis) {
        java.util.Set<String> allowed = analysis.allowedTools();
        if (allowed.isEmpty()) {
            return "none";
        }
        if (analysis.classification() != null) {
            String preferred = switch (analysis.classification().intent()) {
                case APP_OPEN -> "open_app";
                case APP_FOCUS -> "focus_app";
                case APP_QUIT -> "quit_app";
                case FILE_READ -> "read_file";
                case FILE_SEARCH -> "search_files";
                case FILE_LIST -> "list_directory";
                case RUNTIME_INSPECT, SYSTEM_INFO -> "inspect_runtime";
                case JAVA_SNIPPET_EXECUTION -> "execute_java_snippet";
                case WEB_SEARCH -> "web_search";
                case WEB_FETCH -> "web_fetch";
                case WEB_CRAWL -> "web_crawl";
                case WEB_RESEARCH -> "web_search";
                default -> null;
            };
            if (preferred != null && allowed.contains(preferred)) {
                return preferred;
            }
        }
        return allowed.iterator().next();
    }

    private static String decisionLabel(Intent intent) {
        if (intent == Intent.INSPECT) {
            return "inspect";
        }
        return intent.name().toLowerCase(Locale.ROOT);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static List<String> mergeEntities(List<String> extracted, Map<String, Object> structured) {
        List<String> merged = new ArrayList<>(extracted);
        if (structured != null) {
            for (Map.Entry<String, Object> entry : structured.entrySet()) {
                if (entry.getValue() != null) {
                    String value = entry.getKey() + "=" + entry.getValue();
                    if (!merged.contains(value)) {
                        merged.add(value);
                    }
                }
            }
        }
        return List.copyOf(merged);
    }

    private boolean shouldContinuePreviousTurn(
            String userMessage,
            ClassificationResult classification,
            DeskContext context
    ) {
        if (context == null || context.previousUserMessage() == null || context.previousUserMessage().isBlank()) {
            return false;
        }
        if (context.awaitingClarification()) {
            return false;
        }
        if (DiscourseActs.isConfirmation(userMessage)) {
            return !looksLikePolicySkip(context.previousAssistantMessage());
        }
        int tokens = OpenNlpCapabilityClassifier.tokens(userMessage).length;
        double medium = properties.getNlp().getClassifier().getMediumConfidence();
        CapabilityIntent intent = classification.intent();
        if (intent.domain() == com.kesh.nlp.capability.CapabilityDomain.LLM
                && classification.confidence() >= medium) {
            return false;
        }
        if (isStandaloneChat(userMessage)) {
            return false;
        }
        if (looksLikePolicySkip(context.previousAssistantMessage())) {
            return false;
        }
        if (intent.domain() == com.kesh.nlp.capability.CapabilityDomain.WRITING) {
            return false;
        }
        if (intent.domain() == com.kesh.nlp.capability.CapabilityDomain.SYSTEM
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.TOOLS
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.TERMINAL
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.WORK
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.JAVA) {
            return false;
        }
        if (OpenNlpCapabilityClassifier.tokens(context.previousUserMessage()).length <= 2) {
            return false;
        }
        boolean shortFollowUp = tokens <= 4 && (intent == CapabilityIntent.CHAT_GENERAL
                || classification.needsClarification()
                || classification.confidence() < medium);
        boolean shortWebFollowUp = tokens <= 2
                && intent.domain() == com.kesh.nlp.capability.CapabilityDomain.WEB;
        return shortFollowUp || shortWebFollowUp;
    }

    private static final Set<String> STANDALONE_CHAT = Set.of(
            "hi", "hello", "hey", "thanks", "thank you", "ok", "okay", "yo", "sup",
            "good morning", "good evening", "nice", "sounds good", "that worked",
            "please continue", "all good", "appreciate it", "that is helpful",
            "stop there", "hello again", "hi friend"
    );

    private static boolean looksLikePolicySkip(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("not connected")
                || lower.contains("i am not sure what you want")
                || lower.contains("terminal tool is turned off")
                || lower.contains("this looks like a");
    }

    private static boolean isStandaloneChat(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").strip().replaceAll("\\s+", " ");
        return STANDALONE_CHAT.contains(normalized);
    }

    private ClassificationResult continuePreviousTurn(
            String userMessage,
            ClassificationResult current,
            DeskContext context
    ) {
        if (DiscourseActs.isConfirmation(userMessage)) {
            if (DiscourseActs.offeredLocalInstallCheck(context.previousAssistantMessage())) {
                String topic = context.previousUserMessage().strip();
                String inspectText = topic + " version installed on this mac";
                java.util.Map<String, Object> entities = new java.util.LinkedHashMap<>(
                        OpenNlpCapabilityClassifier.entitiesFor(inspectText, CapabilityIntent.RUNTIME_INSPECT)
                );
                entities.put("query", inspectText);
                return ClassificationResult.single(
                        CapabilityIntent.RUNTIME_INSPECT,
                        0.93,
                        entities,
                        "turn-continuation"
                );
            }
            ClassificationResult previous = capabilityClassifier.classify(context.previousUserMessage(), context);
            java.util.Map<String, Object> entities = new java.util.LinkedHashMap<>(previous.entities());
            entities.put("query", context.previousUserMessage().strip());
            return ClassificationResult.single(
                    previous.intent(),
                    Math.max(previous.confidence(), 0.85),
                    entities,
                    "turn-continuation"
            );
        }
        String combined = context.previousUserMessage().strip() + " " + userMessage.strip();
        ClassificationResult again = capabilityClassifier.classify(combined, context);
        if (again.intent().domain() == com.kesh.nlp.capability.CapabilityDomain.CHAT
                || again.needsClarification()) {
            ClassificationResult previous = capabilityClassifier.classify(context.previousUserMessage(), context);
            if (previous.intent().domain() != com.kesh.nlp.capability.CapabilityDomain.CHAT) {
                again = previous;
            }
        }
        java.util.Map<String, Object> entities = new java.util.LinkedHashMap<>(again.entities());
        entities.put("query", combined);
        java.util.List<com.kesh.nlp.capability.ClassifiedIntent> intents = new java.util.ArrayList<>();
        intents.add(new com.kesh.nlp.capability.ClassifiedIntent(
                again.intent().domain(), again.intent(), Math.max(again.confidence(), 0.85), entities
        ));
        for (com.kesh.nlp.capability.ClassifiedIntent item : again.intents()) {
            if (item.intent() != again.intent()) {
                intents.add(new com.kesh.nlp.capability.ClassifiedIntent(
                        item.domain(), item.intent(), item.confidence(), entities
                ));
            }
        }
        return new ClassificationResult(intents, "turn-continuation", false, null);
    }

    private static ClassificationResult applyFollowUp(
            String userMessage,
            ClassificationResult classification,
            DeskContext context
    ) {
        if (context == null || !context.awaitingClarification()) {
            return classification;
        }
        CapabilityIntent followIntent = classification.intent();
        if (followIntent.domain() == com.kesh.nlp.capability.CapabilityDomain.CHAT
                || followIntent.domain() == com.kesh.nlp.capability.CapabilityDomain.LLM) {
            followIntent = CapabilityIntent.LLM_EXPLAIN;
        }
        String original = context.previousUserMessage();
        if (original == null || original.isBlank()) {
            original = userMessage;
        }
        String queryText = OpenNlpCapabilityClassifier.tokens(userMessage).length <= 2
                ? original
                : userMessage;
        java.util.Map<String, Object> entities = new java.util.LinkedHashMap<>(
                OpenNlpCapabilityClassifier.entitiesFor(queryText, followIntent)
        );
        entities.put("query", original);
        java.util.List<com.kesh.nlp.capability.ClassifiedIntent> intents = new java.util.ArrayList<>();
        intents.add(new com.kesh.nlp.capability.ClassifiedIntent(
                followIntent.domain(), followIntent, Math.max(classification.confidence(), 0.9), entities
        ));
        for (com.kesh.nlp.capability.ClassifiedIntent item : classification.intents()) {
            if (item.intent() != followIntent) {
                intents.add(new com.kesh.nlp.capability.ClassifiedIntent(
                        item.domain(), item.intent(), item.confidence(), entities
                ));
            }
        }
        return new ClassificationResult(intents, "clarification-reply", false, null);
    }

    private static String stringEntity(ClassificationResult classification, String key, String fallback) {
        if (classification != null && classification.entities().get(key) != null) {
            String value = classification.entities().get(key).toString();
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static boolean isStaticExplainAsk(String text) {
        if (text == null || text.isBlank() || WebLexicon.isAnyWebAsk(text)
                || CurrentKnowledgeLexicon.needsLivePublicFacts(text)) {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.matches("(?s).*\\b(tell me about|explain|what is|what are|describe|how does|how do)\\b.*");
    }

    private static boolean isWebCapability(ClassificationResult classification) {
        return classification != null
                && classification.intent() != null
                && classification.intent().domain() == com.kesh.nlp.capability.CapabilityDomain.WEB;
    }

    private static ClassificationResult currentTopicRetrieval(
            String userMessage,
            ClassificationResult current,
            DeskContext context,
            String reference
    ) {
        String query = previousTopicQuery(context, userMessage);
        java.util.Map<String, Object> entities = new java.util.LinkedHashMap<>();
        if (current != null) {
            entities.putAll(current.entities());
        }
        entities.put("query", query);
        entities.put("isFollowUp", true);
        entities.put("referencesPreviousContext", true);
        entities.put("reference", reference);
        entities.put("reuseContext", false);
        entities.put("freshnessOverride", true);
        entities.put("knowledgeRequirement", KnowledgeRequirement.CURRENT_KNOWLEDGE.name());
        return ClassificationResult.single(
                CapabilityIntent.WEB_SEARCH,
                0.93,
                entities,
                "current-topic-retrieval"
        );
    }

    private static String previousTopicQuery(DeskContext context, String fallback) {
        if (context != null && context.conversationState() != null
                && hasText(context.conversationState().lastWebQuery())) {
            return context.conversationState().lastWebQuery().strip();
        }
        if (context != null && hasText(context.previousUserMessage())) {
            return context.previousUserMessage().strip();
        }
        return fallback;
    }

    private static boolean shouldForceCurrentWeb(
            String userMessage,
            ClassificationResult classification,
            KnowledgeRequirement knowledge,
            ReferenceResolver.Decision reference
    ) {
        if (classification != null && classification.needsClarification()) {
            return false;
        }
        if (CommandLexicon.isJavaExecutionAsk(userMessage) || CommandLexicon.isImperativeShellAsk(userMessage)) {
            return false;
        }
        CapabilityIntent intent = classification == null ? null : classification.intent();
        if (intent != null && (intent.domain() == com.kesh.nlp.capability.CapabilityDomain.WORK
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.SYSTEM
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.JAVA
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.TERMINAL
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.WRITING
                || intent.domain() == com.kesh.nlp.capability.CapabilityDomain.TOOLS)) {
            return false;
        }
        if (isWebCapability(classification)) {
            return false;
        }
        return knowledge == KnowledgeRequirement.CURRENT_KNOWLEDGE
                || knowledge == KnowledgeRequirement.EXTERNAL_RESEARCH
                || CurrentKnowledgeLexicon.needsLivePublicFacts(userMessage)
                || (reference != null && reference.isFollowUp() && reference.requiresFreshData());
    }

    private static java.util.Map<String, Object> signalEntities(
            String userMessage,
            ClassificationResult classification,
            KnowledgeRequirement knowledge,
            DeskContext context
    ) {
        java.util.Map<String, Object> extra = new java.util.LinkedHashMap<>();
        extra.put("knowledgeRequirement", knowledge == null ? KnowledgeRequirement.UNKNOWN.name() : knowledge.name());
        boolean followUp = classification != null && classification.flag("isFollowUp");
        if (!followUp && context != null
                && (hasText(context.previousAssistantMessage()) || hasText(context.previousUserMessage()))
                && FollowUpResolver.referencesPrevious(userMessage)) {
            extra.put("isFollowUp", true);
            extra.put("referencesPreviousContext", true);
        } else if (classification != null && classification.entity("isFollowUp") == null) {
            extra.put("isFollowUp", false);
            extra.put("referencesPreviousContext", classification.flag("referencesPreviousContext"));
        }
        return extra;
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private static String knowledgeLabel(RequestAnalysis analysis) {
        if (analysis == null || analysis.classification() == null) {
            return KnowledgeRequirement.UNKNOWN.name();
        }
        Object value = analysis.classification().entity("knowledgeRequirement");
        return value == null ? KnowledgeRequirement.UNKNOWN.name() : value.toString();
    }
}
