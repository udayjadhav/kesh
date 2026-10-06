package com.kesh.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.tools.ToolResult;

/**
 * Builds a bounded Gemma context with provenance. Does not authorize tools.
 */
@Component
public class ContextAssembler {

    private final KeshProperties properties;

    public ContextAssembler(KeshProperties properties) {
        this.properties = properties;
    }

    public AssembledContext assemble(
            String userMessage,
            List<RelatedSnippet> related,
            List<ToolResult> toolResults
    ) {
        int maxTokens = Math.max(256, properties.getNlp().getContext().getMaxTokens());
        int maxDocs = Math.max(1, properties.getNlp().getContext().getMaxDocuments());
        int maxTool = Math.max(1, properties.getNlp().getContext().getMaxToolResults());
        int budget = maxTokens * 4;

        List<ContextChunk> chunks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (userMessage != null && !userMessage.isBlank()) {
            chunks.add(new ContextChunk("USER_REQUEST", "local", userMessage.strip(), true));
        }
        int docs = 0;
        if (related != null) {
            for (RelatedSnippet snippet : related) {
                if (docs >= maxDocs) {
                    break;
                }
                String key = "rag:" + (snippet.conversationId() == null ? snippet.excerpt() : snippet.conversationId());
                if (!seen.add(key)) {
                    continue;
                }
                String body = (snippet.title() == null ? "chat" : snippet.title()) + ": " + snippet.excerpt();
                chunks.add(new ContextChunk("RAG_RESULTS", "local", trim(body, 800), true));
                docs++;
            }
        }
        int tools = 0;
        if (toolResults != null) {
            for (ToolResult result : toolResults) {
                if (tools >= maxTool || result == null) {
                    continue;
                }
                String rendered = result.renderForModel();
                if (rendered == null || rendered.isBlank()) {
                    continue;
                }
                String key = "tool:" + result.tool() + ":" + rendered.hashCode();
                if (!seen.add(key)) {
                    continue;
                }
                boolean local = result.sources() == null || result.sources().isEmpty();
                String kind = local ? "TOOL_RESULTS" : "WEB_RESULTS";
                chunks.add(new ContextChunk(kind, local ? "local" : "web", trim(rendered, 2_400), local));
                tools++;
            }
        }
        List<ContextChunk> fitted = new ArrayList<>();
        int used = 0;
        for (ContextChunk chunk : chunks) {
            int cost = chunk.text().length();
            if (used + cost > budget && !fitted.isEmpty()) {
                break;
            }
            fitted.add(chunk);
            used += cost;
        }
        return new AssembledContext(fitted, used / 4);
    }

    public String promptBlock(AssembledContext assembled) {
        if (assembled == null || assembled.chunks().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContextChunk chunk : assembled.chunks()) {
            if ("USER_REQUEST".equals(chunk.kind())) {
                continue;
            }
            if (sb.isEmpty()) {
                sb.append("Assembled context (ranked, truncated, provenance kept):\n");
            }
            sb.append('[').append(chunk.kind()).append(" | ").append(chunk.origin()).append("]\n");
            sb.append(chunk.text()).append('\n');
        }
        return sb.toString();
    }

    private static String trim(String text, int max) {
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= max) {
            return compact;
        }
        return compact.substring(0, max) + "…";
    }

    public record ContextChunk(String kind, String origin, String text, boolean local) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", kind);
            map.put("origin", origin);
            map.put("local", local);
            map.put("chars", text == null ? 0 : text.length());
            return map;
        }
    }

    public record AssembledContext(List<ContextChunk> chunks, int approxTokens) {
        public AssembledContext {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }

        public List<Map<String, Object>> sourceMaps() {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ContextChunk chunk : chunks) {
                rows.add(chunk.toMap());
            }
            return rows;
        }
    }
}
