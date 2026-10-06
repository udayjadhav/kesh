package com.kesh.writing;

import java.util.Locale;

import com.kesh.nlp.capability.CapabilityIntent;

/**
 * Builds a WritingPlan from the classified operation. Deterministic; no LLM.
 */
public final class WritingPlanner {

    private WritingPlanner() {
    }

    public static WritingPlan plan(CapabilityIntent intent, String instruction, String source) {
        CapabilityIntent op = intent == null ? CapabilityIntent.WRITING_REWRITE : intent;
        WritingChannel medium = WritingChannel.from(op, join(instruction, source));
        String audience = audience(join(instruction, source));
        if (op == CapabilityIntent.WRITING_FORMAT && medium == WritingChannel.GENERIC && "TEAM".equals(audience)) {
            medium = WritingChannel.SLACK;
        }
        return new WritingPlan(
                "WRITING",
                operationName(op),
                medium,
                audience,
                tone(op, medium),
                intensity(op),
                preserveMeaning(op, instruction),
                true,
                op == CapabilityIntent.WRITING_FORMAT || op.writingCorrection()
        );
    }

    static String operationName(CapabilityIntent intent) {
        if (intent == null) {
            return "REWRITE";
        }
        String name = intent.name();
        return name.startsWith("WRITING_") ? name.substring("WRITING_".length()) : name;
    }

    private static String intensity(CapabilityIntent intent) {
        if (intent == null) {
            return "MEDIUM";
        }
        if (intent.writingCorrection() || intent == CapabilityIntent.WRITING_FORMAT
                || intent == CapabilityIntent.WRITING_PRESERVE_MEANING) {
            return "LOW";
        }
        if (intent == CapabilityIntent.WRITING_REWRITE
                || intent == CapabilityIntent.WRITING_EXPAND
                || intent == CapabilityIntent.WRITING_DRAFT_EMAIL
                || intent == CapabilityIntent.WRITING_DRAFT_CONFLUENCE
                || intent == CapabilityIntent.WRITING_DRAFT_PR) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private static String tone(CapabilityIntent intent, WritingChannel medium) {
        if (intent == CapabilityIntent.WRITING_FORMAT) {
            return "NATURAL_PROFESSIONAL";
        }
        return switch (intent) {
            case WRITING_PROFESSIONAL -> "PROFESSIONAL";
            case WRITING_CASUAL -> "CASUAL";
            case WRITING_FRIENDLY -> "FRIENDLY";
            case WRITING_DIPLOMATIC -> "DIPLOMATIC";
            case WRITING_CONFIDENT -> "CONFIDENT";
            case WRITING_NATURALIZE -> "NATURAL";
            case WRITING_DRAFT_SLACK -> "SLACK";
            case WRITING_DRAFT_EMAIL -> "EMAIL";
            default -> medium == WritingChannel.SLACK ? "SLACK" : "NATURAL_PROFESSIONAL";
        };
    }

    private static boolean preserveMeaning(CapabilityIntent intent, String instruction) {
        if (intent == CapabilityIntent.WRITING_PRESERVE_MEANING
                || intent == CapabilityIntent.WRITING_FORMAT
                || (intent != null && intent.writingCorrection())) {
            return true;
        }
        if (instruction == null) {
            return false;
        }
        String lower = instruction.toLowerCase(Locale.ROOT);
        return lower.contains("keep my wording")
                || lower.contains("don't change the meaning")
                || lower.contains("without changing the meaning");
    }

    private static String audience(String text) {
        if (text == null) {
            return "UNSPECIFIED";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("hi team") || lower.contains("hey team") || lower.contains("team,")) {
            return "TEAM";
        }
        if (lower.contains("recruiter") || lower.contains("hiring")) {
            return "RECRUITER";
        }
        if (lower.contains("manager")) {
            return "MANAGER";
        }
        return "UNSPECIFIED";
    }

    private static String join(String instruction, String source) {
        String left = instruction == null ? "" : instruction;
        String right = source == null ? "" : source;
        return (left + "\n" + right).strip();
    }
}
