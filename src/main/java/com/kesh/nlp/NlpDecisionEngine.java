package com.kesh.nlp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.nlp.CommandLexicon;
import com.kesh.nlp.capability.CapabilityDomain;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.routing.RoutingPlan;
import com.kesh.routing.RoutingStep;
import com.kesh.tools.Tool;
import com.kesh.workspace.WorkspaceMode;

/**
 * Policy engine. Classification says what the user asked for. This class says whether Kesh may do it.
 */
@Component
public class NlpDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(NlpDecisionEngine.class);

    private static final Set<String> FILE_TOOLS = Set.of(
            "list_directory", "read_file", "search_files"
    );
    private static final Set<String> APP_TOOLS = Set.of(
            "list_running_apps", "open_app", "focus_app", "quit_app"
    );
    private static final Set<String> WORK_TOOLS = Set.of(
            "get_unread_slack_messages", "get_slack_mentions", "get_my_jira_issues",
            "search_confluence", "get_unread_emails", "get_recent_github_prs"
    );
    private static final Set<String> TERMINAL_TOOLS = Set.of("run_command");
    private static final Set<String> JAVA_TOOLS = Set.of("execute_java_snippet");
    private static final Set<String> WEB_SEARCH_TOOLS = Set.of("web_search");
    private static final Set<String> WEB_FETCH_TOOLS = Set.of("web_fetch");

    private final KeshProperties properties;

    public NlpDecisionEngine(KeshProperties properties) {
        this.properties = properties;
    }

    public RequestAnalysis decide(
            WorkspaceMode mode,
            String message,
            List<Tool> catalog,
            Intent intent,
            double intentConfidence,
            Risk risk,
            double riskConfidence,
            List<String> entities
    ) {
        return decide(mode, message, catalog, intent, intentConfidence, risk, riskConfidence, entities, null, null);
    }

    public RequestAnalysis decide(
            WorkspaceMode mode,
            String message,
            List<Tool> catalog,
            Intent intent,
            double intentConfidence,
            Risk risk,
            double riskConfidence,
            List<String> entities,
            ClassificationResult classification,
            RoutingPlan plan
    ) {
        Set<String> registered = names(catalog);
        if (classification != null && classification.needsClarification()) {
            log.info("POLICY CLARIFY intent={}", classification.intent());
            return skip(
                    intent, intentConfidence, risk, riskConfidence, entities,
                    classification.clarificationQuestion(),
                    classification,
                    plan
            );
        }
        if (RequestAnalyzer.isClockQuestion(message) && registered.contains("current_time")
                && intent != Intent.TERMINAL && intent != Intent.WORK) {
            return new RequestAnalysis(
                    Intent.CHAT,
                    intentConfidence,
                    Risk.READ,
                    riskConfidence,
                    entities,
                    true,
                    Set.of("current_time"),
                    false,
                    null,
                    classification,
                    plan
            );
        }
        boolean terminalRequested = intent == Intent.TERMINAL || hasIntent(plan, CapabilityIntent.TERMINAL_EXECUTION);
        boolean otherRequested = hasNonTerminalWork(plan, intent);
        if (terminalRequested && !properties.getTerminal().isEnabled()) {
            if (!otherRequested) {
                log.info("POLICY DENY terminal-disabled");
                return skip(
                        Intent.TERMINAL, intentConfidence, risk, riskConfidence, entities,
                        "This looks like a shell command. The terminal tool is turned off. "
                                + "Ask in chat, or set kesh.terminal.enabled to true if you really need it.",
                        classification,
                        plan
                );
            }
        }
        if (intent == Intent.WORK && !containsAny(registered, WORK_TOOLS)) {
            log.info("POLICY DENY work-unconfigured");
            return skip(
                    intent, intentConfidence, risk, riskConfidence, entities,
                    workSkipMessage(message, plan),
                    classification,
                    plan
            );
        }
        if (isWebIntent(intent) || hasDomain(plan, "WEB")) {
            Intent webIntent = isWebIntent(intent) ? intent : webIntentFromPlan(plan);
            RequestAnalysis blocked = webSkip(webIntent, intentConfidence, risk, riskConfidence, entities, registered);
            if (blocked != null) {
                log.info("POLICY DENY web-disabled");
                return new RequestAnalysis(
                        blocked.intent(), blocked.intentConfidence(), blocked.risk(), blocked.riskConfidence(),
                        blocked.entities(), blocked.restrictTools(), blocked.allowedTools(), blocked.skipModel(),
                        blocked.skipMessage(), classification, plan
                );
            }
        }
        Set<String> tools = plan == null || plan.steps().isEmpty()
                ? allowed(intent, registered)
                : allowed(plan, intent, classification, registered);
        if (terminalRequested && !properties.getTerminal().isEnabled()) {
            tools = without(tools, "run_command");
        }
        if (offerJavaHelper(classification, intent, message) && registered.contains("execute_java_snippet")) {
            tools = new LinkedHashSet<>(tools);
            tools.add("execute_java_snippet");
        } else {
            tools = without(tools, "execute_java_snippet");
        }
        log.info("POLICY ALLOW desk={} intent={} tools={}", mode == null ? "any" : mode.path(), intent, tools);
        return new RequestAnalysis(
                intent,
                intentConfidence,
                risk,
                riskConfidence,
                entities,
                true,
                tools,
                false,
                null,
                classification,
                plan
        );
    }

    private Set<String> allowed(
            RoutingPlan plan,
            Intent fallback,
            ClassificationResult classification,
            Set<String> registered
    ) {
        if (classification != null && classification.intent() == CapabilityIntent.WEB_SEARCH) {
            return allowed(Intent.WEB_SEARCH, registered);
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (RoutingStep step : plan.steps()) {
            wanted.addAll(toolsFor(step.intent()));
        }
        if (wanted.isEmpty()) {
            return allowed(fallback, registered);
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (String name : wanted) {
            if (registered.contains(name)) {
                allowed.add(name);
            }
        }
        return allowed;
    }

    private Set<String> allowed(Intent intent, Set<String> registered) {
        Set<String> wanted = switch (intent) {
            case FILES -> FILE_TOOLS;
            case APPS -> APP_TOOLS;
            case TERMINAL -> union(FILE_TOOLS, TERMINAL_TOOLS);
            case WORK -> WORK_TOOLS;
            case DESTRUCTIVE -> union(union(FILE_TOOLS, APP_TOOLS), TERMINAL_TOOLS);
            case INSPECT -> Set.of("inspect_runtime");
            case JAVA_SNIPPET -> JAVA_TOOLS;
            case WEB_SEARCH -> WEB_SEARCH_TOOLS;
            case WEB_FETCH -> WEB_FETCH_TOOLS;
            case WEB_RESEARCH -> researchTools();
            case CHAT, WRITING -> Set.of();
        };
        Set<String> allowed = new LinkedHashSet<>();
        for (String name : wanted) {
            if (registered.contains(name)) {
                allowed.add(name);
            }
        }
        return allowed;
    }

    private Set<String> toolsFor(CapabilityIntent intent) {
        if (intent == null) {
            return Set.of();
        }
        if (intent.domain() == CapabilityDomain.WRITING) {
            return Set.of();
        }
        return switch (intent) {
            case FILE_READ, FILE_SEARCH, FILE_LIST, FILE_METADATA, GIT,
                    RAG_QUERY, RAG_SUMMARIZE, RAG_COMPARE, RAG_DOCUMENT_RESEARCH -> FILE_TOOLS;
            case APP_OPEN, APP_FOCUS, APP_QUIT -> APP_TOOLS;
            case TERMINAL_EXECUTION -> TERMINAL_TOOLS;
            case JAVA_SNIPPET_EXECUTION -> JAVA_TOOLS;
            case SLACK, JIRA, CONFLUENCE, GITHUB, GMAIL, CALENDAR -> WORK_TOOLS;
            case RUNTIME_INSPECT -> Set.of("inspect_runtime");
            case SYSTEM_INFO -> Set.of("list_running_apps");
            case WEB_SEARCH -> WEB_SEARCH_TOOLS;
            case WEB_FETCH -> WEB_FETCH_TOOLS;
            case WEB_RESEARCH, WEB_CRAWL -> researchTools();
            case DESTRUCTIVE -> union(union(FILE_TOOLS, APP_TOOLS), TERMINAL_TOOLS);
            case CHAT_GENERAL, LLM_EXPLAIN, LLM_COMPARE, LLM_DESIGN, LLM_CODE_HELP, LLM_SUMMARIZE, LLM_FORMAT
                    -> Set.of();
            default -> Set.of();
        };
    }

    private RequestAnalysis webSkip(
            Intent intent,
            double intentConfidence,
            Risk risk,
            double riskConfidence,
            List<String> entities,
            Set<String> registered
    ) {
        if (!properties.getWeb().isEnabled()) {
            return skip(
                    intent, intentConfidence, risk, riskConfidence, entities,
                    "Web research is turned off. Set kesh.web.enabled to true if you need public internet lookup."
            );
        }
        if (intent == Intent.WEB_SEARCH && (!properties.getWeb().searchAvailable() || !registered.contains("web_search"))) {
            return skip(
                    intent, intentConfidence, risk, riskConfidence, entities,
                    "Web search is turned off."
            );
        }
        if (intent == Intent.WEB_FETCH && (!properties.getWeb().fetchAvailable() || !registered.contains("web_fetch"))) {
            return skip(
                    intent, intentConfidence, risk, riskConfidence, entities,
                    "Web fetch is turned off."
            );
        }
        if (intent == Intent.WEB_RESEARCH && (!properties.getWeb().searchAvailable() || !registered.contains("web_search"))) {
            return skip(
                    intent, intentConfidence, risk, riskConfidence, entities,
                    "Web research is turned off."
            );
        }
        return null;
    }

    private Set<String> researchTools() {
        Set<String> wanted = new LinkedHashSet<>();
        wanted.add("web_search");
        if (properties.getWeb().fetchAvailable()) {
            wanted.add("web_fetch");
        }
        if (properties.getWeb().crawlAvailable()) {
            wanted.add("web_crawl");
        }
        return wanted;
    }

    private static boolean isWebIntent(Intent intent) {
        return intent == Intent.WEB_SEARCH || intent == Intent.WEB_FETCH || intent == Intent.WEB_RESEARCH;
    }

    private static boolean hasDomain(RoutingPlan plan, String domain) {
        if (plan == null) {
            return false;
        }
        for (RoutingStep step : plan.steps()) {
            if (step.domain() != null && domain.equals(step.domain().name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIntent(RoutingPlan plan, CapabilityIntent intent) {
        if (plan == null) {
            return false;
        }
        for (RoutingStep step : plan.steps()) {
            if (step.intent() == intent) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonTerminalWork(RoutingPlan plan, Intent intent) {
        if (plan != null) {
            for (RoutingStep step : plan.steps()) {
                if (step.intent() != null && step.intent() != CapabilityIntent.TERMINAL_EXECUTION) {
                    return true;
                }
            }
        }
        return intent != Intent.TERMINAL && intent != Intent.CHAT;
    }

    private boolean offerJavaHelper(ClassificationResult classification, Intent intent, String message) {
        if (!properties.getJavaExecution().isEnabled()) {
            return false;
        }
        if (intent == Intent.TERMINAL || intent == Intent.DESTRUCTIVE) {
            return false;
        }
        if (classification == null) {
            return false;
        }
        CapabilityIntent cap = classification.intent();
        if (cap == null) {
            return false;
        }
        if (cap == CapabilityIntent.JAVA_SNIPPET_EXECUTION || intent == Intent.JAVA_SNIPPET) {
            return true;
        }
        return CommandLexicon.isJavaExecutionAsk(message);
    }

    private static Intent webIntentFromPlan(RoutingPlan plan) {
        if (hasIntent(plan, CapabilityIntent.WEB_FETCH)) {
            return Intent.WEB_FETCH;
        }
        if (hasIntent(plan, CapabilityIntent.WEB_RESEARCH) || hasIntent(plan, CapabilityIntent.WEB_CRAWL)) {
            return Intent.WEB_RESEARCH;
        }
        return Intent.WEB_SEARCH;
    }

    private static RequestAnalysis skip(
            Intent intent,
            double intentConfidence,
            Risk risk,
            double riskConfidence,
            List<String> entities,
            String message
    ) {
        return skip(intent, intentConfidence, risk, riskConfidence, entities, message, null, null);
    }

    private static RequestAnalysis skip(
            Intent intent,
            double intentConfidence,
            Risk risk,
            double riskConfidence,
            List<String> entities,
            String message,
            ClassificationResult classification,
            RoutingPlan plan
    ) {
        return new RequestAnalysis(
                intent, intentConfidence, risk, riskConfidence, entities,
                true, Set.of(), true, message, classification, plan
        );
    }

    private static String workSkipMessage(String text, RoutingPlan plan) {
        String lower = text == null ? "" : text.toLowerCase();
        String app = "Slack, Jira, Gmail, Confluence, or GitHub";
        if (hasIntent(plan, CapabilityIntent.SLACK) || lower.contains("slack")) {
            app = "Slack";
        } else if (hasIntent(plan, CapabilityIntent.JIRA) || lower.contains("jira")) {
            app = "Jira";
        } else if (hasIntent(plan, CapabilityIntent.CONFLUENCE) || lower.contains("confluence")) {
            app = "Confluence";
        } else if (hasIntent(plan, CapabilityIntent.GMAIL) || lower.contains("gmail") || lower.contains("email") || lower.contains("inbox")) {
            app = "Gmail";
        } else if (hasIntent(plan, CapabilityIntent.GITHUB) || lower.contains("github") || lower.contains("pull request") || lower.contains(" pr")) {
            app = "GitHub";
        } else if (hasIntent(plan, CapabilityIntent.CALENDAR) || lower.contains("calendar")) {
            app = "Calendar";
        }
        return "This looks like a " + app + " request. That app is not connected. "
                + "Set the env vars in the README and enable kesh.integrations.<app>.enabled.";
    }

    private static Set<String> names(List<Tool> catalog) {
        Set<String> names = new LinkedHashSet<>();
        for (Tool tool : catalog) {
            names.add(tool.name());
        }
        return names;
    }

    private static boolean containsAny(Set<String> registered, Set<String> wanted) {
        for (String name : wanted) {
            if (registered.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static Set<String> without(Set<String> tools, String name) {
        Set<String> out = new LinkedHashSet<>(tools);
        out.remove(name);
        return out;
    }
}
