package com.kesh.llm.ollama;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.llm.ChatTranscriptNormalizer;
import com.kesh.llm.LlmClient;
import com.kesh.llm.LlmMessage;
import com.kesh.llm.LlmRequest;
import com.kesh.llm.LlmResponse;
import com.kesh.llm.StreamConsumer;
import com.kesh.llm.ToolCallParser;
import com.kesh.tools.ToolCall;

public class OllamaClient implements LlmClient {

    private final String baseUrl;
    private final String defaultModel;
    private final int requestTimeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ToolCallParser parser;

    public OllamaClient(KeshProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this(properties, httpClient, objectMapper, new ToolCallParser(objectMapper));
    }

    public OllamaClient(
            KeshProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ToolCallParser parser
    ) {
        this(
                properties.getLlm().getBaseUrl(),
                properties.getLlm().getModel(),
                properties.getLlm().getRequestTimeoutMs(),
                httpClient,
                objectMapper,
                parser
        );
    }

    public OllamaClient(
            String baseUrl,
            String defaultModel,
            int requestTimeoutMs,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this(baseUrl, defaultModel, requestTimeoutMs, httpClient, objectMapper, new ToolCallParser(objectMapper));
    }

    public OllamaClient(
            String baseUrl,
            String defaultModel,
            int requestTimeoutMs,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ToolCallParser parser
    ) {
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.requestTimeoutMs = requestTimeoutMs;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.parser = parser;
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        Collector collector = new Collector();
        chatStreaming(request, collector);
        if (collector.error != null) {
            return LlmResponse.failure(collector.error.getMessage());
        }
        return collector.response != null ? collector.response : LlmResponse.failure("Empty LLM response");
    }

    @Override
    public void chatStreaming(LlmRequest request, StreamConsumer consumer) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", request.model() == null ? defaultModel : request.model());
            body.put("stream", true);
            body.put("messages", toOllamaMessages(ChatTranscriptNormalizer.forChatModel(request.messages())));
            String json = objectMapper.writeValueAsString(body);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(baseUrl) + "/api/chat"))
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                consumer.onError(new IllegalStateException("Ollama HTTP " + response.statusCode() + ": " + error));
                return;
            }
            StringBuilder content = new StringBuilder();
            List<ToolCall> nativeCalls = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(line);
                    if (node.has("error") && !node.get("error").isNull()) {
                        consumer.onError(new IllegalStateException(node.get("error").asText()));
                        return;
                    }
                    JsonNode message = node.get("message");
                    if (message != null && message.has("content") && !message.get("content").isNull()) {
                        String token = message.get("content").asText();
                        if (!token.isEmpty()) {
                            content.append(token);
                            consumer.onToken(token);
                        }
                    }
                    if (message != null && message.has("tool_calls")) {
                        nativeCalls.addAll(parser.fromNativeToolCalls(message.get("tool_calls")));
                    }
                }
            }
            String full = content.toString();
            List<ToolCall> parsed = nativeCalls.isEmpty() ? parser.parse(full) : nativeCalls;
            consumer.onComplete(parsed.isEmpty() ? LlmResponse.text(full) : LlmResponse.tools(full, parsed));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            consumer.onError(e);
        } catch (Exception e) {
            consumer.onError(e);
        }
    }

    private List<Map<String, Object>> toOllamaMessages(List<LlmMessage> messages) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (LlmMessage message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            String role = message.role();
            if ("tool".equals(role)) {
                item.put("role", "user");
                item.put("content", message.content());
            } else {
                item.put("role", role);
                item.put("content", message.content() == null ? "" : message.content());
            }
            mapped.add(item);
        }
        return mapped;
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private static final class Collector implements StreamConsumer {
        private LlmResponse response;
        private Throwable error;

        @Override
        public void onToken(String token) {
            // collected in onComplete
        }

        @Override
        public void onComplete(LlmResponse response) {
            this.response = response;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }
}
