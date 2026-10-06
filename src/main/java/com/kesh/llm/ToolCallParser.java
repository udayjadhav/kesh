package com.kesh.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.tools.ToolCall;

@Component
public class ToolCallParser {

    private static final Pattern TOOL_BLOCK = Pattern.compile(
            "<tool_call>\\s*(\\{.*?})\\s*</tool_call>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern FENCED_JSON = Pattern.compile(
            "```(?:json)?\\s*(\\{\\s*\"tool\".*?})\\s*```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern FAKE_TOOL_XML = Pattern.compile(
            "(?is)<(web_search|web_fetch|web_crawl|web_research|search)\\b[^>]*>.*?</\\1>"
    );
    private static final Pattern FAKE_TOOL_OPEN = Pattern.compile(
            "(?is)<(web_search|web_fetch|web_crawl|web_research|search)\\b"
    );
    private static final Pattern FAKE_TOOL_JSON_FENCE = Pattern.compile(
            "(?is)```(?:json)?\\s*\\{\\s*\"tool\"\\s*:\\s*\"web_search\".*?```"
    );

    private final ObjectMapper objectMapper;

    public ToolCallParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ToolCall> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        int searchFrom = 0;
        String lower = content.toLowerCase();
        while (true) {
            int tag = lower.indexOf("<tool_call>", searchFrom);
            if (tag < 0) {
                break;
            }
            int jsonStart = content.indexOf('{', tag);
            String json = extractJsonObject(content, jsonStart);
            if (json != null) {
                parseJson(json).ifPresent(calls::add);
            }
            searchFrom = tag + 11;
        }
        if (!calls.isEmpty()) {
            return List.copyOf(calls);
        }
        Matcher fenced = FENCED_JSON.matcher(content);
        while (fenced.find()) {
            String json = extractJsonObject(fenced.group(1), 0);
            if (json != null) {
                parseJson(json).ifPresent(calls::add);
            }
        }
        if (!calls.isEmpty()) {
            return List.copyOf(calls);
        }
        String trimmed = content.trim();
        if (trimmed.contains("\"tool\"")) {
            String json = extractJsonObject(trimmed, trimmed.indexOf('{'));
            if (json != null) {
                parseJson(json).ifPresent(calls::add);
            }
        }
        return List.copyOf(calls);
    }

    static String extractJsonObject(String text, int startHint) {
        if (text == null || startHint < 0 || startHint >= text.length()) {
            return null;
        }
        int start = text.indexOf('{', startHint);
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    public boolean looksLikeToolCall(String content) {
        if (content == null) {
            return false;
        }
        return looksLikeStructuredToolCall(content) || looksLikeInvalidToolProse(content);
    }

    public boolean looksLikeStructuredToolCall(String content) {
        if (content == null) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.contains("<tool_call>")
                || (trimmed.startsWith("{") && trimmed.contains("\"tool\""))
                || (trimmed.startsWith("```") && trimmed.contains("\"tool\""));
    }

    public boolean looksLikeInvalidToolProse(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if (FAKE_TOOL_XML.matcher(content).find() || FAKE_TOOL_OPEN.matcher(content).find()) {
            return true;
        }
        String trimmed = content.strip();
        if (looksLikeNamedToolTag(trimmed)) {
            return true;
        }
        return FAKE_TOOL_JSON_FENCE.matcher(content).find()
                && parse(content).isEmpty();
    }

    public boolean looksLikeToolTagStart(String content) {
        if (content == null) {
            return false;
        }
        String peek = content.stripLeading();
        if (!peek.startsWith("<")) {
            return false;
        }
        String lower = peek.toLowerCase();
        if (looksLikeNamedToolTag(lower)) {
            return true;
        }
        return peek.length() < 16 && lower.matches("<[a-z_]*");
    }

    private static boolean looksLikeNamedToolTag(String text) {
        String lower = text.toLowerCase();
        return lower.contains("<web_search")
                || lower.contains("<web_fetch")
                || lower.contains("<web_crawl")
                || lower.contains("<web_research")
                || lower.startsWith("<search")
                || lower.startsWith("<tool_call")
                || lower.startsWith("<tool>");
    }

    public String stripToolMarkup(String content) {
        if (content == null) {
            return "";
        }
        String stripped = TOOL_BLOCK.matcher(content).replaceAll("").trim();
        stripped = FENCED_JSON.matcher(stripped).replaceAll("").trim();
        stripped = FAKE_TOOL_XML.matcher(stripped).replaceAll("").trim();
        stripped = FAKE_TOOL_OPEN.matcher(stripped).replaceAll("").trim();
        return stripped;
    }

    @SuppressWarnings("unchecked")
    public List<ToolCall> fromNativeToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode node : toolCallsNode) {
            JsonNode function = node.has("function") ? node.get("function") : node;
            String name = text(function, "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            String id = text(node, "id");
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            Map<String, Object> arguments = new LinkedHashMap<>();
            JsonNode argsNode = function.get("arguments");
            if (argsNode != null && argsNode.isTextual()) {
                arguments.putAll(readArguments(argsNode.asText()));
            } else if (argsNode != null && argsNode.isObject()) {
                arguments.putAll(objectMapper.convertValue(argsNode, Map.class));
            }
            calls.add(new ToolCall(id, name, arguments));
        }
        return List.copyOf(calls);
    }

    private java.util.Optional<ToolCall> parseJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String name = text(node, "tool");
            if (name == null) {
                name = text(node, "name");
            }
            if (name == null || name.isBlank()) {
                return java.util.Optional.empty();
            }
            Map<String, Object> arguments = new LinkedHashMap<>();
            JsonNode args = node.get("arguments");
            if (args != null && args.isObject()) {
                arguments.putAll(objectMapper.convertValue(args, Map.class));
            }
            return java.util.Optional.of(new ToolCall(UUID.randomUUID().toString(), name, arguments));
        } catch (JsonProcessingException e) {
            return java.util.Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readArguments(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isObject()) {
                return objectMapper.convertValue(node, Map.class);
            }
        } catch (JsonProcessingException ignored) {
            // fall through
        }
        return Map.of();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }
}
