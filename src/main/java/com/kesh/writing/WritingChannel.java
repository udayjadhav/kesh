package com.kesh.writing;

import com.kesh.nlp.capability.CapabilityIntent;

public enum WritingChannel {
    GENERIC,
    EMAIL,
    SLACK,
    REPLY,
    JIRA,
    CONFLUENCE,
    PR;

    public static WritingChannel from(CapabilityIntent intent, String text) {
        if (intent != null) {
            return switch (intent) {
                case WRITING_DRAFT_EMAIL -> EMAIL;
                case WRITING_DRAFT_SLACK -> SLACK;
                case WRITING_DRAFT_REPLY -> REPLY;
                case WRITING_DRAFT_JIRA -> JIRA;
                case WRITING_DRAFT_CONFLUENCE -> CONFLUENCE;
                case WRITING_DRAFT_PR -> PR;
                default -> fromText(text);
            };
        }
        return fromText(text);
    }

    private static WritingChannel fromText(String text) {
        if (text == null || text.isBlank()) {
            return GENERIC;
        }
        String lower = text.toLowerCase();
        if (lower.contains("slack")) {
            return SLACK;
        }
        if (lower.contains("jira") || lower.contains("ticket")) {
            return JIRA;
        }
        if (lower.contains("confluence") || lower.contains("runbook")) {
            return CONFLUENCE;
        }
        if (lower.contains("pull request") || lower.contains(" pr ") || lower.endsWith(" pr")
                || lower.contains("pr description")) {
            return PR;
        }
        if (lower.contains("email") || lower.contains("recruiter") || lower.contains("subject:")) {
            return EMAIL;
        }
        if (lower.contains("reply")) {
            return REPLY;
        }
        return GENERIC;
    }
}
