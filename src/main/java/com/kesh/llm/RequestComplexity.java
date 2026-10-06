package com.kesh.llm;

import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.capability.CapabilityIntent;

public enum RequestComplexity {
    LOW,
    MEDIUM,
    HIGH;

    public static RequestComplexity of(RequestAnalysis analysis, String userMessage) {
        CapabilityIntent intent = analysis == null || analysis.classification() == null
                ? null
                : analysis.classification().intent();
        int chars = userMessage == null ? 0 : userMessage.strip().length();
        if (intent != null && intent.domain() == com.kesh.nlp.capability.CapabilityDomain.WRITING) {
            if (intent.writingCorrection() || intent == CapabilityIntent.WRITING_FORMAT) {
                return LOW;
            }
            if (intent == CapabilityIntent.WRITING_DRAFT_EMAIL
                    || intent == CapabilityIntent.WRITING_DRAFT_PR
                    || intent == CapabilityIntent.WRITING_DRAFT_CONFLUENCE
                    || intent == CapabilityIntent.WRITING_DIPLOMATIC
                    || chars > 400) {
                return HIGH;
            }
            return chars > 120 ? MEDIUM : LOW;
        }
        if (intent == CapabilityIntent.LLM_DESIGN
                || intent == CapabilityIntent.LLM_COMPARE
                || intent == CapabilityIntent.WEB_RESEARCH
                || intent == CapabilityIntent.RAG_DOCUMENT_RESEARCH
                || chars > 400) {
            return HIGH;
        }
        if (intent == CapabilityIntent.LLM_CODE_HELP
                || intent == CapabilityIntent.LLM_EXPLAIN
                || intent == CapabilityIntent.JAVA_SNIPPET_EXECUTION
                || intent == CapabilityIntent.WEB_FETCH
                || chars > 120) {
            return MEDIUM;
        }
        return LOW;
    }
}
