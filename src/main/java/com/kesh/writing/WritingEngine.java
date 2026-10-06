package com.kesh.writing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kesh.agent.AgentEvent;
import com.kesh.agent.AgentEventListener;
import com.kesh.agent.AgentResponse;
import com.kesh.agent.ConversationContext;
import com.kesh.config.KeshProperties;
import com.kesh.llm.LlmClient;
import com.kesh.llm.LlmMessage;
import com.kesh.llm.LlmRequest;
import com.kesh.llm.LlmResponse;
import com.kesh.llm.ModelRouter;
import com.kesh.llm.RoutingDecision;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.capability.CapabilityDomain;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.routing.RoutingPlan;
import com.kesh.routing.RoutingStep;
import com.kesh.workspace.WorkspaceMode;

/**
 * Writing pipelines on top of LanguageTool + local models. Does not send mail or chat.
 */
@Component
public class WritingEngine {

    private static final Logger log = LoggerFactory.getLogger(WritingEngine.class);

    private final LanguageToolGrammarChecker checker;
    private final LlmClient llmClient;
    private final ModelRouter modelRouter;
    private final WritingPreferenceStore preferences;
    private final WritingTurnStore turns;
    private final KeshProperties properties;

    public WritingEngine(
            LanguageToolGrammarChecker checker,
            LlmClient llmClient,
            ModelRouter modelRouter,
            WritingPreferenceStore preferences,
            WritingTurnStore turns,
            KeshProperties properties
    ) {
        this.checker = checker;
        this.llmClient = llmClient;
        this.modelRouter = modelRouter;
        this.preferences = preferences;
        this.turns = turns;
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.getWriting().isEnabled();
    }

    public static boolean isWritingOnly(RequestAnalysis analysis) {
        if (analysis == null || analysis.intent() != com.kesh.nlp.Intent.WRITING) {
            return false;
        }
        RoutingPlan plan = analysis.plan();
        if (plan == null || plan.steps().isEmpty()) {
            return true;
        }
        for (RoutingStep step : plan.steps()) {
            if (step == null || step.domain() == null) {
                continue;
            }
            if (step.domain() == CapabilityDomain.WRITING || step.domain() == CapabilityDomain.LLM
                    || step.domain() == CapabilityDomain.CHAT) {
                continue;
            }
            return false;
        }
        return true;
    }

    public AgentResponse handle(
            ConversationContext context,
            RequestAnalysis analysis,
            String userMessage,
            AgentEventListener listener,
            String interactionId
    ) {
        AgentEventListener events = listener == null ? AgentEventListener.noop() : listener;
        CapabilityIntent intent = analysis == null || analysis.classification() == null
                ? CapabilityIntent.WRITING_REWRITE
                : analysis.classification().intent();
        String previousUser = context == null ? "" : previousUserBefore(context, userMessage);
        String previousAssistant = context == null ? "" : context.lastAssistantMessage();
        WritingPayload payload = WritingPayload.extract(userMessage, intent, previousUser, previousAssistant);
        WritingPlan plan = WritingPlanner.plan(intent, payload.instruction(), payload.source());
        boolean preserve = preserveMeaning(analysis, payload.instruction()) || plan.preserveMeaning();
        log.info("WRITING intent={} op={} intensity={} channel={} sourceChars={} preserve={}",
                intent, plan.operation(), plan.rewriteIntensity(), payload.channel(),
                payload.source() == null ? 0 : payload.source().length(), preserve);

        WritingMode mode = intent.writingCorrection() ? WritingMode.CORRECTION
                : intent == CapabilityIntent.WRITING_FORMAT ? WritingMode.FORMAT
                : looksLikeDraft(intent) ? WritingMode.DRAFT
                : WritingMode.REWRITE;
        String output;
        LanguageToolGrammarChecker.CorrectionResult grammar = null;
        if (mode == WritingMode.CORRECTION) {
            grammar = checker.correct(payload.hasSource() ? payload.source() : userMessage, intent, preserve);
            output = grammar.corrected();
        } else if (mode == WritingMode.FORMAT) {
            String seed = payload.hasSource() ? payload.source() : userMessage;
            grammar = checker.correct(seed, CapabilityIntent.WRITING_CORRECT_GRAMMAR, false);
            String prepared = grammar.changed() ? grammar.corrected() : seed;
            output = generate(analysis, userMessage, payload.withSource(prepared), preserve, context, plan);
            output = stripWrapping(output);
            if (inventedCommitment(seed, output)) {
                String retry = generate(analysis, userMessage, payload.withSource(prepared), true, context, plan);
                retry = stripWrapping(retry);
                output = inventedCommitment(seed, retry) ? prepared : retry;
            }
            LanguageToolGrammarChecker.CorrectionResult polish =
                    checker.correct(output, CapabilityIntent.WRITING_CORRECT_GRAMMAR, false);
            if (polish.changed()
                    && !inventedCommitment(seed, polish.corrected())
                    && !lostNames(seed, polish.corrected())) {
                output = polish.corrected();
                grammar = polish;
            }
        } else {
            output = generate(analysis, userMessage, payload, preserve, context, plan);
            String beforeGrammar = output;
            grammar = checker.correct(output, CapabilityIntent.WRITING_CORRECT_GRAMMAR, false);
            if (grammar.changed() && !lostNames(payload.hasSource() ? payload.source() : beforeGrammar, grammar.corrected())) {
                output = grammar.corrected();
            } else if (grammar.changed()) {
                output = beforeGrammar;
            }
            if (properties.getWriting().isSemanticCheck() && payload.hasSource() && mode != WritingMode.FORMAT) {
                double threshold = properties.getWriting().getSemanticThreshold();
                boolean strict = preserve || intent == CapabilityIntent.WRITING_PRESERVE_MEANING;
                boolean factsMissing = !SemanticPreservation.missingFacts(payload.source(), output).isEmpty();
                boolean drifted = strict && !SemanticPreservation.preserved(payload.source(), output, threshold);
                if (factsMissing || drifted) {
                    String retry = generate(analysis, userMessage, payload, true, context, plan);
                    LanguageToolGrammarChecker.CorrectionResult retryGrammar =
                            checker.correct(retry, CapabilityIntent.WRITING_CORRECT_GRAMMAR, false);
                    retry = retryGrammar.changed() ? retryGrammar.corrected() : retry;
                    if (!SemanticPreservation.missingFacts(payload.source(), retry).isEmpty() && factsMissing && !strict) {
                        // keep first draft if retry also drops facts
                    } else if (!strict || SemanticPreservation.preserved(payload.source(), retry, threshold)
                            || SemanticPreservation.missingFacts(payload.source(), retry).isEmpty()) {
                        output = retry;
                    }
                }
            }
        }
        String original = payload.hasSource() ? payload.source() : userMessage;
        Map<String, Double> scores = WritingEvaluator.scores(
                original,
                output,
                intent == CapabilityIntent.WRITING_SHORTEN
                        ? WritingEvaluator.CapabilityIntentKind.SHORTEN
                        : intent == CapabilityIntent.WRITING_EXPAND
                        ? WritingEvaluator.CapabilityIntentKind.EXPAND
                        : WritingEvaluator.CapabilityIntentKind.OTHER,
                preserve,
                grammar == null || !grammar.changed() || grammar.engineAvailable()
        );
        Map<String, Object> writing = toClient(mode, intent, original, output, scores, payload.channel(), plan, payload.instruction());
        if (interactionId != null) {
            turns.put(new WritingTurnStore.WritingTurn(
                    interactionId,
                    context == null ? "" : context.id(),
                    context == null || context.mode() == null ? "work" : context.mode().path(),
                    intent.name(),
                    payload.channel().name().toLowerCase(),
                    original,
                    output,
                    scores
            ));
        }
        if (context != null) {
            context.addAssistant(output);
        }
        events.onEvent(AgentEvent.done(context == null ? "" : context.id(), output, List.of(), writing));
        return AgentResponse.completed(context == null ? "" : context.id(), output, List.of()).withWriting(writing);
    }

    private String generate(
            RequestAnalysis             analysis,
            String userMessage,
            WritingPayload payload,
            boolean preserve,
            ConversationContext context
    ) {
        return generate(analysis, userMessage, payload, preserve, context, null);
    }

    private String generate(
            RequestAnalysis analysis,
            String userMessage,
            WritingPayload payload,
            boolean preserve,
            ConversationContext context,
            WritingPlan plan
    ) {
        CapabilityIntent intent = analysis.classification() == null
                ? CapabilityIntent.WRITING_REWRITE
                : analysis.classification().intent();
        RoutingDecision decision = modelRouter.decide(analysis, userMessage);
        String model = decision == null ? properties.getLlm().getModel() : decision.model();
        WorkspaceMode mode = context == null ? null : context.mode();
        String conversation = conversationClip(context);
        LlmResponse response = llmClient.chat(new LlmRequest(
                model,
                List.of(
                        LlmMessage.system(WritingPrompts.system(
                                intent,
                                payload.channel(),
                                preserve,
                                preferences.hint(mode),
                                plan
                        )),
                        LlmMessage.user(WritingPrompts.user(payload.instruction(), payload.source(), conversation))
                ),
                false
        ));
        if (response == null || response.failed() || response.content() == null || response.content().isBlank()) {
            return payload.hasSource() ? payload.source() : "I could not draft that locally. Try again.";
        }
        return stripWrapping(response.content());
    }

    private static String stripWrapping(String text) {
        String stripped = text.strip();
        if (stripped.startsWith("```") && stripped.endsWith("```")) {
            int first = stripped.indexOf('\n');
            if (first > 0) {
                stripped = stripped.substring(first + 1, stripped.length() - 3).strip();
            }
        }
        stripped = stripped.replaceFirst(
                "(?is)^(here is (the )?(formatted|rewritten|corrected|professional|updated) (version|message|text)[:.\\s]*"
                        + "|sure!?\\s*(i(?:'ve| have) )?(rewritten|formatted|corrected)[^.\\n]*[.\\n]\\s*"
                        + "|formatted version:\\s*)",
                ""
        ).strip();
        return stripped;
    }

    private static final java.util.regex.Pattern INVENTED_COMMITMENT = java.util.regex.Pattern.compile(
            "(?i)\\b(i['’]?ll be back|back tomorrow|return on|out of office until|sick leave|flu|covid|doctor['’]?s (note|appointment))\\b"
    );

    static boolean inventedCommitment(String original, String revised) {
        if (revised == null || revised.isBlank()) {
            return false;
        }
        java.util.regex.Matcher matcher = INVENTED_COMMITMENT.matcher(revised);
        while (matcher.find()) {
            String fact = matcher.group();
            if (original == null || !original.toLowerCase().contains(fact.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    static boolean lostNames(String original, String revised) {
        if (original == null || original.isBlank() || revised == null) {
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b[A-Z][a-zA-Z]{2,}\\b").matcher(original);
        String lower = revised.toLowerCase();
        while (matcher.find()) {
            String name = matcher.group();
            if ("Hi".equals(name) || "The".equals(name) || "This".equals(name) || "That".equals(name)
                    || "Traffic".equals(name) || "Team".equals(name)) {
                continue;
            }
            if (!lower.contains(name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String conversationClip(ConversationContext context) {
        if (context == null) {
            return "";
        }
        String previous = context.lastAssistantMessage();
        if (previous == null || previous.isBlank()) {
            return "";
        }
        return previous.length() > 1200 ? previous.substring(0, 1200) + "…" : previous;
    }

    private static String previousUserBefore(ConversationContext context, String current) {
        if (context == null) {
            return "";
        }
        String last = context.lastUserMessage();
        if (last != null && current != null && last.strip().equals(current.strip())) {
            return "";
        }
        return last == null ? "" : last;
    }

    static boolean preserveMeaning(RequestAnalysis analysis, String instruction) {
        if (instruction != null) {
            String lower = instruction.toLowerCase();
            if (lower.contains("keep my wording")
                    || lower.contains("don't change my wording")
                    || lower.contains("do not change my wording")
                    || lower.contains("without changing the meaning")
                    || lower.contains("don't change the meaning")) {
                return true;
            }
        }
        return preserveMeaning(analysis);
    }

    static boolean preserveMeaning(RequestAnalysis analysis) {
        ClassificationResult classification = analysis == null ? null : analysis.classification();
        if (classification == null) {
            return false;
        }
        if (classification.intent() == CapabilityIntent.WRITING_PRESERVE_MEANING
                || classification.intent() == CapabilityIntent.WRITING_CORRECT_GRAMMAR
                || classification.intent() == CapabilityIntent.WRITING_CORRECT_SPELLING
                || classification.intent() == CapabilityIntent.WRITING_FIX_PUNCTUATION
                || classification.intent() == CapabilityIntent.WRITING_FORMAT) {
            return classification.intent() != CapabilityIntent.WRITING_REWRITE;
        }
        return classification.intents().stream().anyMatch(item ->
                item.intent() == CapabilityIntent.WRITING_PRESERVE_MEANING);
    }

    private static boolean looksLikeDraft(CapabilityIntent intent) {
        return intent == CapabilityIntent.WRITING_DRAFT_EMAIL
                || intent == CapabilityIntent.WRITING_DRAFT_SLACK
                || intent == CapabilityIntent.WRITING_DRAFT_REPLY
                || intent == CapabilityIntent.WRITING_DRAFT_JIRA
                || intent == CapabilityIntent.WRITING_DRAFT_CONFLUENCE
                || intent == CapabilityIntent.WRITING_DRAFT_PR;
    }

    static Map<String, Object> toClient(
            WritingMode mode,
            CapabilityIntent intent,
            String original,
            String output,
            Map<String, Double> scores,
            WritingChannel channel,
            WritingPlan plan,
            String instruction
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", mode.name());
        map.put("label", label(mode, intent));
        map.put("intent", intent == null ? "" : intent.name());
        map.put("channel", channel == null ? "" : channel.name());
        map.put("original", original == null ? "" : original);
        map.put("output", output == null ? "" : output);
        map.put("instruction", instruction == null ? "" : instruction);
        map.put("diff", WritingDiff.tokens(original, output));
        map.put("scores", scores);
        if (plan != null) {
            map.put("plan", plan.toMap());
        }
        return map;
    }

    private static String label(WritingMode mode, CapabilityIntent intent) {
        if (mode == WritingMode.CORRECTION) {
            return "Corrected";
        }
        if (mode == WritingMode.FORMAT) {
            return "Formatted";
        }
        if (intent == null) {
            return "Rewrite";
        }
        return switch (intent) {
            case WRITING_FORMAT -> "Formatted";
            case WRITING_PROFESSIONAL -> "Professional";
            case WRITING_CASUAL -> "Casual";
            case WRITING_FRIENDLY -> "Friendly";
            case WRITING_DIPLOMATIC -> "Diplomatic";
            case WRITING_CONFIDENT -> "Confident";
            case WRITING_NATURALIZE -> "Natural";
            case WRITING_SHORTEN -> "Shorter";
            case WRITING_EXPAND -> "Expanded";
            case WRITING_DRAFT_EMAIL -> "Email draft";
            case WRITING_DRAFT_SLACK -> "Slack";
            case WRITING_DRAFT_REPLY -> "Reply";
            case WRITING_DRAFT_JIRA -> "Jira comment";
            case WRITING_DRAFT_CONFLUENCE -> "Confluence";
            case WRITING_DRAFT_PR -> "PR description";
            case WRITING_SUMMARIZE -> "Summary";
            default -> "Rewrite";
        };
    }
}
