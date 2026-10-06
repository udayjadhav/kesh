package com.kesh.nlp.capability;

/**
 * What kind of knowledge a turn needs. Technical topics are not automatically web searches.
 */
public enum KnowledgeRequirement {
    STATIC_KNOWLEDGE,
    CURRENT_KNOWLEDGE,
    USER_KNOWLEDGE,
    COMPANY_KNOWLEDGE,
    EXTERNAL_RESEARCH,
    UNKNOWN;

    public static KnowledgeRequirement parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(raw.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
