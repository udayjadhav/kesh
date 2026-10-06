package com.kesh.writing;

import com.kesh.nlp.capability.CapabilityIntent;

final class WritingPrompts {

    private WritingPrompts() {
    }

    static String system(
            CapabilityIntent intent,
            WritingChannel channel,
            boolean preserveMeaning,
            String preferenceHint
    ) {
        return system(intent, channel, preserveMeaning, preferenceHint, null);
    }

    static String system(
            CapabilityIntent intent,
            WritingChannel channel,
            boolean preserveMeaning,
            String preferenceHint,
            WritingPlan plan
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Kesh, a local writing assistant for engineering communication.\n");
        sb.append("Return only the rewritten or drafted text. No preface. No analysis.\n");
        sb.append("Do not write 'Here is the formatted version' or 'Sure, I rewrote this'.\n");
        sb.append("Preserve factual meaning, names, dates, numbers, URLs, technical terms, company names, job titles, identifiers, and placeholders.\n");
        sb.append("Do not invent information, recipient email addresses, illness details, return dates, or implementation details.\n");
        sb.append("Do not send messages. Draft only.\n");
        sb.append("Avoid corporate filler, excessive politeness, generic AI phrases, and over-explanation.\n");
        if (plan != null) {
            sb.append("Plan: operation=").append(plan.operation())
                    .append(" medium=").append(plan.medium())
                    .append(" audience=").append(plan.audience())
                    .append(" tone=").append(plan.tone())
                    .append(" rewriteIntensity=").append(plan.rewriteIntensity()).append('\n');
        }
        sb.append(tone(intent, channel, plan));
        if ((preserveMeaning || (plan != null && plan.preserveMeaning()))
                && intent != CapabilityIntent.WRITING_FORMAT
                && (plan == null || !"FORMAT".equals(plan.operation()))) {
            sb.append("Keep the original wording wherever possible. Change only what the instruction requires.\n");
        }
        if (preferenceHint != null && !preferenceHint.isBlank()) {
            sb.append("User writing preferences:\n").append(preferenceHint.strip()).append('\n');
        }
        return sb.toString();
    }

    static String user(String instruction, String source, String conversation) {
        StringBuilder sb = new StringBuilder();
        if (instruction != null && !instruction.isBlank()) {
            sb.append("Instruction:\n").append(instruction.strip()).append("\n\n");
        }
        if (conversation != null && !conversation.isBlank()) {
            sb.append("Relevant previous messages:\n").append(conversation.strip()).append("\n\n");
        }
        if (source != null && !source.isBlank()) {
            sb.append("Original text:\n").append(source.strip()).append('\n');
        } else {
            sb.append("There is no original text. Draft from the instruction. Do not invent an email address.\n");
        }
        return sb.toString();
    }

    private static String tone(CapabilityIntent intent, WritingChannel channel, WritingPlan plan) {
        StringBuilder sb = new StringBuilder();
        if (intent == CapabilityIntent.WRITING_FORMAT || (plan != null && "FORMAT".equals(plan.operation()))) {
            sb.append("FORMAT, not rewrite. Fix spacing, capitalization, punctuation, sentence boundaries, and obvious spelling.\n");
            sb.append("Turn fragments into natural short sentences when the meaning is already clear. Example: 'day off' can become 'Taking the day off today.'\n");
            sb.append("Do not invent illness, dates, return dates, reasons, or commitments. Do not add 'I'll be back tomorrow' unless the original said that.\n");
            sb.append("Keep it concise and human. Slack-like if it is a team message: greeting, blank line, then the note.\n");
        } else if (intent != null) {
            sb.append(switch (intent) {
                case WRITING_SHORTEN -> "Make it shorter. Keep the same meaning. Keep time words such as currently, now, today, latest.\n";
                case WRITING_EXPAND -> "Add a little necessary detail. Do not pad.\n";
                case WRITING_PROFESSIONAL -> "Use clear professional language. Prefer \"Could you please share an update on this?\" over stiff corporate phrasing.\n";
                case WRITING_CASUAL -> "Keep it casual and direct.\n";
                case WRITING_FRIENDLY -> "Keep it warm and human, not gushy.\n";
                case WRITING_DIPLOMATIC -> "Soften the edge. Stay honest. Do not become vague.\n";
                case WRITING_CONFIDENT -> "Sound like a senior engineer: calm, specific, no swagger.\n";
                case WRITING_NATURALIZE -> "Make it sound like a real person wrote it. Remove AI-sounding filler.\n";
                case WRITING_SUMMARIZE -> "Summarize. Keep facts. Keep time words such as currently, now, today, yesterday, recently, latest, at the time. Drop repetition.\n";
                case WRITING_DRAFT_EMAIL -> "Produce Subject plus email body. Do not invent recipient addresses.\n";
                case WRITING_DRAFT_SLACK -> "Write a Slack message: concise, conversational, easy to scan. Not an email.\n";
                case WRITING_DRAFT_REPLY -> "Write a natural reply. Do not repeat the previous thread unless needed.\n";
                case WRITING_DRAFT_JIRA -> "Write a concise technical Jira comment: facts, status, next action.\n";
                case WRITING_DRAFT_CONFLUENCE -> "Write structured documentation with short headings and precise language.\n";
                case WRITING_DRAFT_PR -> "Write a PR title, summary, motivation, implementation, testing, risks, and rollout notes. Do not invent code changes.\n";
                default -> "Follow the instruction closely.\n";
            });
        }
        if (channel == WritingChannel.SLACK && intent != CapabilityIntent.WRITING_DRAFT_EMAIL) {
            sb.append("This will be posted in Slack. Keep it short.\n");
        }
        if (channel == WritingChannel.EMAIL) {
            sb.append("This is email. Include a subject line unless the user already supplied one.\n");
        }
        return sb.toString();
    }
}
