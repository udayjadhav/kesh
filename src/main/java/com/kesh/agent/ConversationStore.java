package com.kesh.agent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.kesh.api.dto.ConversationSummary;
import com.kesh.persistence.ConversationArchive;
import com.kesh.persistence.DeskPreferenceStore;
import com.kesh.persistence.StoredConversation;
import com.kesh.tools.ToolRegistry;
import com.kesh.workspace.WorkspaceMode;

@Component
public class ConversationStore {

    private static final int MAX_RAM = 200;

    private final ConcurrentHashMap<String, ConversationContext> conversations = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;
    private final ConversationArchive archive;
    private final DeskPreferenceStore preferences;

    public ConversationStore(
            ToolRegistry toolRegistry,
            ConversationArchive archive,
            DeskPreferenceStore preferences
    ) {
        this.toolRegistry = toolRegistry;
        this.archive = archive;
        this.preferences = preferences;
    }

    public ConversationContext getOrCreate(WorkspaceMode mode, String conversationId) {
        WorkspaceMode desk = mode == null ? WorkspaceMode.PROFESSIONAL : mode;
        String id = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId;
        String key = key(desk, id);
        ConversationContext existing = conversations.get(key);
        if (existing != null) {
            return existing;
        }
        StoredConversation stored = archive.load(desk, id);
        if (stored != null) {
            if (stored.mode() != null && stored.mode() != desk) {
                throw new IllegalArgumentException("Unknown conversation: " + id);
            }
            ConversationContext restored = ConversationContext.restore(stored);
            conversations.put(key, restored);
            return restored;
        }
        evictIfNeeded(desk);
        ConversationContext created = new ConversationContext(id, desk, prompt(desk));
        conversations.put(key, created);
        return created;
    }

    public ConversationContext require(WorkspaceMode mode, String conversationId) {
        ConversationContext context = find(mode, conversationId);
        if (context == null) {
            throw new IllegalArgumentException("Unknown conversation: " + conversationId);
        }
        return context;
    }

    public ConversationContext find(WorkspaceMode mode, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        ConversationContext ram = conversations.get(key(mode, conversationId));
        if (ram != null) {
            return ram;
        }
        StoredConversation stored = archive.load(mode, conversationId);
        if (stored == null) {
            return null;
        }
        ConversationContext restored = ConversationContext.restore(stored);
        conversations.put(key(mode, conversationId), restored);
        return restored;
    }

    public void save(ConversationContext context) {
        StoredConversation stored = context.toStored();
        archive.save(stored);
        conversations.put(key(context.mode(), context.id()), context);
    }

    public List<ConversationSummary> recent(WorkspaceMode mode) {
        ConcurrentHashMap<String, ConversationContext> merged = new ConcurrentHashMap<>();
        conversations.forEach((key, context) -> {
            if (context.mode() == mode) {
                merged.put(context.id(), context);
            }
        });
        for (StoredConversation stored : archive.loadAll(mode)) {
            merged.putIfAbsent(stored.id(), ConversationContext.restore(stored));
        }
        return merged.values().stream()
                .filter(context -> context.lastUserMessage() != null && !context.lastUserMessage().isBlank())
                .sorted(Comparator.comparing(ConversationContext::updatedAt).reversed())
                .map(context -> new ConversationSummary(context.id(), context.title(), context.updatedAt()))
                .toList();
    }

    public Map<String, ConversationContext> snapshot() {
        return Map.copyOf(conversations);
    }

    private String prompt(WorkspaceMode mode) {
        String base = toolRegistry.systemPrompt(mode);
        String note = preferences.note(mode);
        if (note == null || note.isBlank()) {
            return base;
        }
        return base + "\n\nUser preference for this desk:\n" + note;
    }

    private void evictIfNeeded(WorkspaceMode mode) {
        long count = conversations.values().stream().filter(context -> context.mode() == mode).count();
        if (count < MAX_RAM) {
            return;
        }
        conversations.values().stream()
                .filter(context -> context.mode() == mode)
                .min(Comparator.comparing(ConversationContext::updatedAt))
                .ifPresent(oldest -> conversations.remove(key(oldest.mode(), oldest.id())));
    }

    private static String key(WorkspaceMode mode, String id) {
        return mode.path() + ":" + id;
    }
}
