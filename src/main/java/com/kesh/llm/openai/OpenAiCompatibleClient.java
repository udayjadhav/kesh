package com.kesh.llm.openai;

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

public class OpenAiCompatibleClient implements LlmClient {

    private final String baseUrl;
    private final String chatPath;
    private final String defaultModel;
    private final String providerName;
    private final int requestTimeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ToolCallParser parser;

    public OpenAiCompatibleClient(KeshProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this(properties, httpClient, objectMapper, new ToolCallParser(objectMapper));
    }

    public OpenAiCompatibleClient(
            KeshProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ToolCallParser parser
    ) {
        this(
                properties.getLlm().getBaseUrl(),
                properties.getLlm().getChatPath(),
                properties.getLlm().getModel(),
                properties.getLlm().getProvider(),
                properties.getLlm().getRequestTimeoutMs(),
                httpClient,
                objectMapper,
                parser
        );
    }

    public OpenAiCompatibleClient(
            String baseUrl,
            String chatPath,
            String defaultModel,
            String providerName,
            int requestTimeoutMs,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this(baseUrl, chatPath, defaultModel, providerName, requestTimeoutMs, httpClient, objectMapper, new ToolCallParser(objectMapper));
    }

    public OpenAiCompatibleClient(
            String baseUrl,
            String chatPath,
            String defaultModel,
            String providerName,
            int requestTimeoutMs,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ToolCallParser parser
    ) {
        this.baseUrl = baseUrl;
        this.chatPath = chatPath;
        this.defaultModel = defaultModel;
        this.providerName = providerName == null ? "docker-model-runner" : providerName;
        this.requestTimeoutMs = requestTimeoutMs;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.parser = parser;
    }

    @Override
    public String providerName() {
        return providerName;
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
            body.put("messages", toOpenAiMessages(ChatTranscriptNormalizer.forChatModel(request.messages())));
            String json = objectMapper.writeValueAsString(body);
            String path = chatPath == null || chatPath.isBlank()
                    ? "/engines/v1/chat/completions"
                    : chatPath;
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(baseUrl) + path))
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                consumer.onError(new IllegalStateException(
                        "LLM HTTP " + response.statusCode() + ": " + error
                ));
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
                    String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(payload);
                    if (node.has("error") && node.get("error").isObject()) {
                        JsonNode errorNode = node.get("error");
                        String message = errorNode.has("message")
                                ? errorNode.get("message").asText()
                                : errorNode.toString();
                        consumer.onError(new IllegalStateException(message));
                        return;
                    }
                    JsonNode choices = node.get("choices");
                    if (choices == null || !choices.isArray() || choices.isEmpty()) {
                        continue;
                    }
                    JsonNode choice = choices.get(0);
                    JsonNode delta = choice.has("delta") ? choice.get("delta") : choice.get("message");
                    if (delta == null) {
                        continue;
                    }
                    if (delta.has("content") && !delta.get("content").isNull()) {
                        String token = delta.get("content").asText();
                        if (!token.isEmpty()) {
                            content.append(token);
                            consumer.onToken(token);
                        }
                    }
                    if (delta.has("tool_calls")) {
                        nativeCalls.addAll(parser.fromNativeToolCalls(delta.get("tool_calls")));
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

    private List<Map<String, Object>> toOpenAiMessages(List<LlmMessage> messages) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (LlmMessage message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            if ("tool".equals(message.role())) {
                item.put("role", "user");
                item.put("content", message.content());
            } else {
                item.put("role", message.role());
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
