package com.kesh.nlp.capability;

import com.kesh.nlp.Intent;

/**
 * Fine-grained user intent. The policy engine maps this to tools and skips.
 */
public enum CapabilityIntent {
    CHAT_GENERAL(CapabilityDomain.CHAT),

    LLM_EXPLAIN(CapabilityDomain.LLM),
    LLM_COMPARE(CapabilityDomain.LLM),
    LLM_DESIGN(CapabilityDomain.LLM),
    LLM_CODE_HELP(CapabilityDomain.LLM),
    LLM_SUMMARIZE(CapabilityDomain.LLM),
    LLM_FORMAT(CapabilityDomain.LLM),

    RUNTIME_INSPECT(CapabilityDomain.SYSTEM),
    APP_OPEN(CapabilityDomain.SYSTEM),
    APP_FOCUS(CapabilityDomain.SYSTEM),
    APP_QUIT(CapabilityDomain.SYSTEM),
    SYSTEM_INFO(CapabilityDomain.SYSTEM),

    FILE_READ(CapabilityDomain.TOOLS),
    FILE_SEARCH(CapabilityDomain.TOOLS),
    FILE_LIST(CapabilityDomain.TOOLS),
    FILE_METADATA(CapabilityDomain.TOOLS),
    GIT(CapabilityDomain.TOOLS),

    WEB_SEARCH(CapabilityDomain.WEB),
    WEB_FETCH(CapabilityDomain.WEB),
    WEB_RESEARCH(CapabilityDomain.WEB),
    WEB_CRAWL(CapabilityDomain.WEB),

    RAG_QUERY(CapabilityDomain.RAG),
    RAG_SUMMARIZE(CapabilityDomain.RAG),
    RAG_COMPARE(CapabilityDomain.RAG),
    RAG_DOCUMENT_RESEARCH(CapabilityDomain.RAG),

    SLACK(CapabilityDomain.WORK),
    JIRA(CapabilityDomain.WORK),
    CONFLUENCE(CapabilityDomain.WORK),
    GITHUB(CapabilityDomain.WORK),
    GMAIL(CapabilityDomain.WORK),
    CALENDAR(CapabilityDomain.WORK),

    TERMINAL_EXECUTION(CapabilityDomain.TERMINAL),
    JAVA_SNIPPET_EXECUTION(CapabilityDomain.JAVA),
    DESTRUCTIVE(CapabilityDomain.TOOLS),

    WRITING_CORRECT_GRAMMAR(CapabilityDomain.WRITING),
    WRITING_CORRECT_SPELLING(CapabilityDomain.WRITING),
    WRITING_FIX_PUNCTUATION(CapabilityDomain.WRITING),
    WRITING_FORMAT(CapabilityDomain.WRITING),
    WRITING_REWRITE(CapabilityDomain.WRITING),
    WRITING_SHORTEN(CapabilityDomain.WRITING),
    WRITING_EXPAND(CapabilityDomain.WRITING),
    WRITING_PROFESSIONAL(CapabilityDomain.WRITING),
    WRITING_CASUAL(CapabilityDomain.WRITING),
    WRITING_FRIENDLY(CapabilityDomain.WRITING),
    WRITING_DIPLOMATIC(CapabilityDomain.WRITING),
    WRITING_CONFIDENT(CapabilityDomain.WRITING),
    WRITING_NATURALIZE(CapabilityDomain.WRITING),
    WRITING_DRAFT_EMAIL(CapabilityDomain.WRITING),
    WRITING_DRAFT_SLACK(CapabilityDomain.WRITING),
    WRITING_DRAFT_REPLY(CapabilityDomain.WRITING),
    WRITING_DRAFT_JIRA(CapabilityDomain.WRITING),
    WRITING_DRAFT_CONFLUENCE(CapabilityDomain.WRITING),
    WRITING_DRAFT_PR(CapabilityDomain.WRITING),
    WRITING_SUMMARIZE(CapabilityDomain.WRITING),
    WRITING_PRESERVE_MEANING(CapabilityDomain.WRITING);

    private final CapabilityDomain domain;

    CapabilityIntent(CapabilityDomain domain) {
        this.domain = domain;
    }

    public CapabilityDomain domain() {
        return domain;
    }

    public Intent legacyIntent() {
        if (domain == CapabilityDomain.WRITING) {
            return Intent.WRITING;
        }
        return switch (this) {
            case CHAT_GENERAL, LLM_EXPLAIN, LLM_COMPARE, LLM_DESIGN, LLM_CODE_HELP, LLM_SUMMARIZE, LLM_FORMAT
                    -> Intent.CHAT;
            case RUNTIME_INSPECT -> Intent.INSPECT;
            case SYSTEM_INFO -> Intent.APPS;
            case APP_OPEN, APP_FOCUS, APP_QUIT -> Intent.APPS;
            case FILE_READ, FILE_SEARCH, FILE_LIST, FILE_METADATA, GIT -> Intent.FILES;
            case WEB_SEARCH -> Intent.WEB_SEARCH;
            case WEB_FETCH -> Intent.WEB_FETCH;
            case WEB_RESEARCH, WEB_CRAWL -> Intent.WEB_RESEARCH;
            case RAG_QUERY, RAG_SUMMARIZE, RAG_COMPARE, RAG_DOCUMENT_RESEARCH -> Intent.FILES;
            case SLACK, JIRA, CONFLUENCE, GITHUB, GMAIL, CALENDAR -> Intent.WORK;
            case TERMINAL_EXECUTION -> Intent.TERMINAL;
            case JAVA_SNIPPET_EXECUTION -> Intent.JAVA_SNIPPET;
            case DESTRUCTIVE -> Intent.DESTRUCTIVE;
            default -> Intent.WRITING;
        };
    }

    public boolean writingCorrection() {
        return this == WRITING_CORRECT_GRAMMAR
                || this == WRITING_CORRECT_SPELLING
                || this == WRITING_FIX_PUNCTUATION;
    }

    public boolean writingGeneration() {
        return domain == CapabilityDomain.WRITING && !writingCorrection();
    }

    public static CapabilityIntent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CHAT_GENERAL;
        }
        String key = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return switch (key) {
                case "CHAT", "GREETING" -> CHAT_GENERAL;
                case "EXPLAIN" -> LLM_EXPLAIN;
                case "COMPARE" -> LLM_COMPARE;
                case "SEARCH" -> WEB_SEARCH;
                case "FETCH" -> WEB_FETCH;
                case "RESEARCH" -> WEB_RESEARCH;
                case "INSPECT" -> RUNTIME_INSPECT;
                case "JAVA_SNIPPET", "EXECUTE_JAVA", "JAVA_SNIPPET_EXECUTION" -> JAVA_SNIPPET_EXECUTION;
                case "GIT_STATUS", "GIT_LOG" -> GIT;
                case "FIX_GRAMMAR", "GRAMMAR" -> WRITING_CORRECT_GRAMMAR;
                case "FORMAT_MESSAGE", "WRITING_FORMAT" -> WRITING_FORMAT;
                case "REWRITE" -> WRITING_REWRITE;
                case "SHORTEN" -> WRITING_SHORTEN;
                case "NATURALIZE" -> WRITING_NATURALIZE;
                case "DRAFT_EMAIL", "EMAIL" -> WRITING_DRAFT_EMAIL;
                case "DRAFT_SLACK", "SLACK_WRITE" -> WRITING_DRAFT_SLACK;
                case "DRAFT_REPLY", "REPLY" -> WRITING_DRAFT_REPLY;
                case "DRAFT_PR", "PR" -> WRITING_DRAFT_PR;
                default -> CHAT_GENERAL;
            };
        }
    }
}
