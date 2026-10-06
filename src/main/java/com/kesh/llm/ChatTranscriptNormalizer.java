package com.kesh.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gemma chat templates require one optional system message, then strict
 * user/assistant/user/assistant turns. Extra system rows (classifier hints)
 * and tool rows mapped to user break that contract.
 */
public final class ChatTranscriptNormalizer {

    private ChatTranscriptNormalizer() {
    }

    public static List<LlmMessage> forChatModel(List<LlmMessage> messages) {
        return forChatModel(messages, null);
    }

    /**
     * @param allowedTools null keeps every tool row; a set keeps only those tools
     */
    public static List<LlmMessage> forChatModel(List<LlmMessage> messages, Set<String> allowedTools) {
        return forChatModel(messages, allowedTools, false);
    }

    public static List<LlmMessage> forChatModel(
            List<LlmMessage> messages,
            Set<String> allowedTools,
            boolean independentTurn
    ) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        StringBuilder system = new StringBuilder();
        List<LlmMessage> turns = new ArrayList<>();
        int lastRealUser = lastRealUserIndex(messages);
        for (int i = 0; i < messages.size(); i++) {
            LlmMessage message = messages.get(i);
            if (message == null) {
                continue;
            }
            String role = message.role();
            if ("system".equals(role)) {
                appendBlock(system, message.content());
                continue;
            }
            if (!keepForModel(message, allowedTools, i, lastRealUser, independentTurn)) {
                continue;
            }
            String mappedRole = "tool".equals(role) ? "user" : role;
            if (!"user".equals(mappedRole) && !"assistant".equals(mappedRole)) {
                mappedRole = "user";
            }
            String content = contentForModel(message);
            if (content.isBlank()) {
                continue;
            }
            if (!turns.isEmpty() && turns.get(turns.size() - 1).role().equals(mappedRole)) {
                LlmMessage previous = turns.remove(turns.size() - 1);
                turns.add(new LlmMessage(mappedRole, previous.content() + "\n\n" + content, List.of(), null));
            } else {
                turns.add(new LlmMessage(mappedRole, content, List.of(), null));
            }
        }
        if (!turns.isEmpty() && "assistant".equals(turns.get(0).role())) {
            turns.add(0, LlmMessage.user("(continue)"));
        }
        List<LlmMessage> out = new ArrayList<>();
        if (!system.isEmpty()) {
            out.add(LlmMessage.system(system.toString()));
        }
        out.addAll(turns);
        return List.copyOf(out);
    }

    private static int lastRealUserIndex(List<LlmMessage> messages) {
        int last = -1;
        for (int i = 0; i < messages.size(); i++) {
            LlmMessage message = messages.get(i);
            if (message == null || !"user".equals(message.role())) {
                continue;
            }
            String content = message.content() == null ? "" : message.content();
            if (content.startsWith("[[kesh-internal]]")) {
                continue;
            }
            last = i;
        }
        return last;
    }

    private static boolean keepForModel(
            LlmMessage message,
            Set<String> allowedTools,
            int index,
            int lastRealUser,
            boolean independentTurn
    ) {
        if (independentTurn && lastRealUser >= 0 && index < lastRealUser
                && message != null && !"system".equals(message.role())) {
            return false;
        }
        boolean staleTool = lastRealUser >= 0 && index < lastRealUser
                && ("tool".equals(message.role()) || !message.toolCalls().isEmpty());
        if (staleTool) {
            return false;
        }
        if (allowedTools == null) {
            return true;
        }
        if (allowedTools.isEmpty()) {
            if ("tool".equals(message.role())) {
                return index > lastRealUser;
            }
            if (!message.toolCalls().isEmpty()) {
                return false;
            }
            String content = message.content() == null ? "" : message.content();
            return !content.contains("TOOL_RESULT") || index > lastRealUser;
        }
        if ("tool".equals(message.role())) {
            return message.toolName() != null && allowedTools.contains(message.toolName());
        }
        if (!message.toolCalls().isEmpty()) {
            return message.toolCalls().stream().anyMatch(call ->
                    call != null && call.tool() != null && allowedTools.contains(call.tool()));
        }
        String content = message.content() == null ? "" : message.content();
        return !content.contains("TOOL_RESULT");
    }

    private static String contentForModel(LlmMessage message) {
        String content = message.content() == null ? "" : message.content().trim();
        if (!content.isEmpty()) {
            return content;
        }
        if (!message.toolCalls().isEmpty()) {
            return "Calling tools: " + message.toolCalls().get(0).tool();
        }
        return "";
    }

    private static void appendBlock(StringBuilder target, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append("\n\n");
        }
        target.append(content.trim());
    }
}
