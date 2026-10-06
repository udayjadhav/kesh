package com.kesh.llm;

import java.net.http.HttpClient;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.llm.ollama.OllamaClient;
import com.kesh.llm.openai.OpenAiCompatibleClient;

/**
 * One orchestrator client. Sends each request to the local runtime that owns the chosen model.
 */
public class RoutedLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RoutedLlmClient.class);

    private final KeshProperties properties;
    private final ModelRouter router;
    private final ModelCatalog catalog;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, LlmClient> clients = new ConcurrentHashMap<>();

    public RoutedLlmClient(
            KeshProperties properties,
            ModelRouter router,
            ModelCatalog catalog,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.router = router;
        this.catalog = catalog;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerName() {
        RoutingDecision decision = RoutingContext.get();
        if (decision != null && decision.provider() != null && !decision.provider().isBlank()) {
            return decision.provider();
        }
        return properties.getLlm().getProvider();
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
        RoutingDecision decision = RoutingContext.get();
        LlmClient primary = clientFor(decision, request.model());
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicReference<RuntimeException> firstError = new AtomicReference<>();
        primary.chatStreaming(request, new StreamConsumer() {
            @Override
            public void onToken(String token) {
                started.set(true);
                consumer.onToken(token);
            }

            @Override
            public void onComplete(LlmResponse response) {
                consumer.onComplete(response);
            }

            @Override
            public void onError(Throwable error) {
                firstError.set(error instanceof RuntimeException
                        ? (RuntimeException) error
                        : new IllegalStateException(error));
            }
        });
        if (firstError.get() == null || started.get()) {
            if (firstError.get() != null) {
                consumer.onError(firstError.get());
            }
            return;
        }
        RoutingDecision fallback = router.fallback(decision);
        if (fallback == null || fallback.model().equals(decision == null ? "" : decision.model())) {
            consumer.onError(firstError.get());
            return;
        }
        log.info("LLM fallback {} -> {}", decision == null ? "" : decision.model(), fallback.model());
        LlmRequest next = new LlmRequest(fallback.model(), request.messages(), request.stream());
        clientFor(fallback, fallback.model()).chatStreaming(next, consumer);
    }

    private LlmClient clientFor(RoutingDecision decision, String modelName) {
        RegisteredModel listed = catalog == null ? null : catalog.find(modelName);
        String provider = listed != null
                ? listed.provider()
                : decision != null && !decision.provider().isBlank()
                ? decision.provider()
                : properties.getLlm().getProvider();
        String baseUrl = listed != null && !listed.baseUrl().isBlank()
                ? listed.baseUrl()
                : decision != null && !decision.baseUrl().isBlank()
                ? decision.baseUrl()
                : properties.getLlm().getBaseUrl();
        String key = provider + "|" + baseUrl;
        return clients.computeIfAbsent(key, ignored -> create(provider, baseUrl));
    }

    private LlmClient create(String provider, String baseUrl) {
        String name = provider == null ? "" : provider.toLowerCase().trim();
        int timeout = properties.getLlm().getRequestTimeoutMs();
        String defaultModel = properties.getLlm().getModel();
        if ("ollama".equals(name)) {
            return new OllamaClient(baseUrl, defaultModel, timeout, httpClient, objectMapper);
        }
        String path = properties.getLlm().getChatPath();
        if (path == null || path.isBlank() || path.contains("/api/chat")) {
            path = "/engines/v1/chat/completions";
        }
        if ("ollama".equalsIgnoreCase(properties.getLlm().getProvider()) && !"ollama".equals(name)) {
            path = "/engines/v1/chat/completions";
        }
        return new OpenAiCompatibleClient(
                baseUrl,
                path,
                defaultModel,
                name.isBlank() ? "docker-model-runner" : name,
                timeout,
                httpClient,
                objectMapper
        );
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
