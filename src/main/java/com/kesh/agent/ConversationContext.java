package com.kesh.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kesh.api.dto.TranscriptMessage;
import com.kesh.nlp.capability.ConversationState;
import com.kesh.llm.LlmMessage;
import com.kesh.persistence.StoredConversation;
import com.kesh.tools.ToolCall;
import com.kesh.tools.ToolResult;
import com.kesh.workspace.WorkspaceMode;

public class ConversationContext {

    private static final String REQUEST_CHECK = "\n\n--- request check ---\n";
    private static final String SAVED_NOTES = "\n\n--- saved notes ---\n";

    private final String id;
    private final WorkspaceMode mode;
    private final Instant createdAt;
    private final List<LlmMessage> messages = new ArrayList<>();
    private String lastUserMessage = "";
    private String title = "New chat";
    private Instant updatedAt;
    private final ConversationState conversationState = new ConversationState();

    public ConversationContext(String id, String systemPrompt) {
        this(id, WorkspaceMode.PROFESSIONAL, systemPrompt);
    }

    public ConversationContext(String id, WorkspaceMode mode, String systemPrompt) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.mode = mode == null ? WorkspaceMode.PROFESSIONAL : mode;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.messages.add(LlmMessage.system(systemPrompt));
    }

    public static ConversationContext restore(StoredConversation stored) {
        ConversationContext context = new ConversationContext(
                stored.id(),
                stored.mode(),
                stored.messages() == null || stored.messages().isEmpty()
                        ? "You are Kesh."
                        : stored.messages().get(0).content()
        );
        context.messages.clear();
        if (stored.messages() != null) {
            context.messages.addAll(stored.messages());
        }
        context.title = stored.title() == null ? "New chat" : stored.title();
        context.lastUserMessage = stored.lastUserMessage() == null ? "" : stored.lastUserMessage();
        if (stored.updatedAt() != null) {
            context.updatedAt = stored.updatedAt();
        }
        return context;
    }

    public WorkspaceMode mode() {
        return mode;
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized Instant updatedAt() {
        return updatedAt;
    }

    public synchronized String title() {
        return title;
    }

    public synchronized void addUser(String content) {
        lastUserMessage = content;
        if ("New chat".equals(title) && content != null && !content.isBlank()) {
            title = truncate(content);
        }
        messages.add(LlmMessage.user(content));
        touch();
    }

    public synchronized void addClassifierHint(String hint) {
        rewriteSystem(currentNotes(), hint);
    }

    public synchronized void addSavedNotes(String notes) {
        rewriteSystem(notes, currentCheck());
    }

    public synchronized StoredConversation toStored() {
        return new StoredConversation(id, mode, title, createdAt, updatedAt, lastUserMessage, messages());
    }

    public static final String INTERNAL_NUDGE_PREFIX = "[[kesh-internal]]\n";

    public synchronized void addModelNudge(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        messages.add(LlmMessage.user(INTERNAL_NUDGE_PREFIX + content));
        touch();
    }

    public synchronized void addAssistant(String content) {
        messages.add(LlmMessage.assistant(content));
        touch();
    }

    public synchronized void addAssistantToolCalls(String content, List<ToolCall> toolCalls) {
        messages.add(LlmMessage.assistantToolCalls(content, toolCalls));
        touch();
    }

    public synchronized void addToolResult(ToolResult result) {
        messages.add(LlmMessage.tool(result.tool(), result.renderForModel()));
        touch();
    }

    public synchronized List<LlmMessage> messages() {
        return List.copyOf(messages);
    }

    public synchronized List<TranscriptMessage> transcript() {
        List<TranscriptMessage> visible = new ArrayList<>();
        for (LlmMessage message : messages) {
            if (!"user".equals(message.role()) && !"assistant".equals(message.role())) {
                continue;
            }
            if (!message.toolCalls().isEmpty()) {
                continue;
            }
            String content = message.content() == null ? "" : message.content().trim();
            if (content.isBlank() || content.contains("<tool_call>")
                    || content.contains("<web_search>")
                    || content.contains("<web_fetch>")
                    || content.toLowerCase().contains("your last tool call was malformed")
                    || content.toLowerCase().contains("respond with a valid <tool_call>")) {
                continue;
            }
            if (content.startsWith(INTERNAL_NUDGE_PREFIX) || isCannedClarification(content)) {
                continue;
            }
            visible.add(new TranscriptMessage(message.role(), content));
        }
        return List.copyOf(visible);
    }

    public synchronized String lastUserMessage() {
        return lastUserMessage;
    }

    public ConversationState conversationState() {
        return conversationState;
    }

    public synchronized String lastAssistantMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage message = messages.get(i);
            if ("assistant".equals(message.role()) && message.toolCalls().isEmpty()) {
                String content = message.content() == null ? "" : message.content().trim();
                if (!content.isBlank() && !content.contains("<tool_call>")
                        && !content.contains("<web_search>")
                        && !content.contains("<web_fetch>")
                        && !content.startsWith(INTERNAL_NUDGE_PREFIX)
                        && !isCannedClarification(content)) {
                    return content;
                }
            }
        }
        return "";
    }

    public synchronized int size() {
        return messages.size();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private synchronized void rewriteSystem(String notes, String check) {
        if (messages.isEmpty() || !"system".equals(messages.get(0).role())) {
            return;
        }
        StringBuilder sb = new StringBuilder(systemBase());
        if (notes != null && !notes.isBlank()) {
            sb.append(SAVED_NOTES).append(notes.trim());
        }
        if (check != null && !check.isBlank()) {
            sb.append(REQUEST_CHECK).append(check.trim());
        }
        messages.set(0, LlmMessage.system(sb.toString()));
        touch();
    }

    private String systemBase() {
        String content = messages.get(0).content() == null ? "" : messages.get(0).content();
        int notes = content.indexOf(SAVED_NOTES);
        int check = content.indexOf(REQUEST_CHECK);
        int cut = content.length();
        if (notes >= 0) {
            cut = Math.min(cut, notes);
        }
        if (check >= 0) {
            cut = Math.min(cut, check);
        }
        return content.substring(0, cut);
    }

    private String currentNotes() {
        String content = messages.get(0).content() == null ? "" : messages.get(0).content();
        int notes = content.indexOf(SAVED_NOTES);
        if (notes < 0) {
            return "";
        }
        int check = content.indexOf(REQUEST_CHECK, notes + SAVED_NOTES.length());
        String body = check < 0 ? content.substring(notes + SAVED_NOTES.length()) : content.substring(notes + SAVED_NOTES.length(), check);
        return body.trim();
    }

    private String currentCheck() {
        String content = messages.get(0).content() == null ? "" : messages.get(0).content();
        int check = content.indexOf(REQUEST_CHECK);
        if (check < 0) {
            return "";
        }
        return content.substring(check + REQUEST_CHECK.length()).trim();
    }

    private static String truncate(String content) {
        String compact = content.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 48) {
            return compact;
        }
        return compact.substring(0, 48) + "…";
    }

    private static boolean isCannedClarification(String content) {
        return content.startsWith("I am not sure what you want.");
    }
}
