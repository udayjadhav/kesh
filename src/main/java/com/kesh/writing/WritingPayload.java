package com.kesh.writing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kesh.nlp.capability.CapabilityIntent;

/**
 * Splits a writing request into the instruction and the text being edited.
 */
public record WritingPayload(
        String instruction,
        String source,
        WritingChannel channel
) {

    private static final Pattern FENCE = Pattern.compile("```(?:\\w+)?\\s*([\\s\\S]*?)```");
    private static final Pattern QUOTED = Pattern.compile("[\"“]([\\s\\S]{8,})[\"”]");
    private static final Pattern TRAILING_INSTRUCTION = Pattern.compile(
            "(?is)^([\\s\\S]{8,}?)\\n+(?:please\\s+)?("
                    + "(?:format|rewrite|fix grammar|fix punctuation|fix spelling|correct grammar"
                    + "|make this|shorten|expand|summarize|summarise|naturalize|proofread)[^\\n]{0,80}"
                    + ")$"
    );

    public static WritingPayload extract(
            String message,
            CapabilityIntent intent,
            String previousUser,
            String previousAssistant
    ) {
        String raw = message == null ? "" : message.strip();
        String source = payloadFrom(raw);
        String instruction = instructionFrom(raw, source);
        if (source.isBlank()) {
            if (looksLikeDraftFromScratch(intent, raw)) {
                source = "";
            } else if (previousAssistant != null && !previousAssistant.isBlank()) {
                source = previousAssistant.strip();
            } else if (previousUser != null && !previousUser.isBlank()) {
                source = previousUser.strip();
            }
        }
        return new WritingPayload(
                instruction.isBlank() ? raw : instruction,
                source,
                WritingChannel.from(intent, raw)
        );
    }

    public WritingPayload withSource(String nextSource) {
        return new WritingPayload(instruction, nextSource == null ? "" : nextSource, channel);
    }

    public boolean hasSource() {
        return source != null && !source.isBlank();
    }

    public static String payloadFrom(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Matcher fence = FENCE.matcher(raw);
        if (fence.find()) {
            return fence.group(1).strip();
        }
        Matcher trailing = TRAILING_INSTRUCTION.matcher(raw);
        if (trailing.find()) {
            return trailing.group(1).strip();
        }
        int colon = indexOfPayloadBreak(raw);
        if (colon >= 0 && colon < raw.length() - 1) {
            String after = raw.substring(colon + 1).strip();
            if (after.length() >= 8) {
                return after;
            }
        }
        Matcher quoted = QUOTED.matcher(raw);
        if (quoted.find()) {
            return quoted.group(1).strip();
        }
        return "";
    }

    private static int indexOfPayloadBreak(String raw) {
        int newlineColon = raw.indexOf(":\n");
        int colon = raw.indexOf(':');
        if (newlineColon >= 0) {
            return newlineColon;
        }
        if (colon >= 0 && raw.length() - colon > 12) {
            return colon;
        }
        return -1;
    }

    private static String instructionFrom(String raw, String source) {
        if (raw == null) {
            return "";
        }
        if (source == null || source.isBlank() || !raw.contains(source)) {
            return raw.strip();
        }
        int at = raw.indexOf(source);
        if (at <= 0) {
            return raw.strip();
        }
        return raw.substring(0, at).replaceFirst("[:\\-]+$", "").strip();
    }

    private static boolean looksLikeDraftFromScratch(CapabilityIntent intent, String raw) {
        if (intent == null) {
            return false;
        }
        return switch (intent) {
            case WRITING_DRAFT_EMAIL, WRITING_DRAFT_SLACK, WRITING_DRAFT_REPLY,
                    WRITING_DRAFT_JIRA, WRITING_DRAFT_CONFLUENCE, WRITING_DRAFT_PR -> true;
            default -> raw != null && raw.toLowerCase().contains("write ");
        };
    }
}
