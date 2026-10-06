package com.kesh.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.llm.LlmClient;
import com.kesh.nlp.capability.LlmIntentClassifierFallback;
import com.kesh.nlp.capability.RemoteSetFitClassifier;
import com.kesh.web.BraveSearchClient;
import com.kesh.web.DuckDuckGoSearchClient;
import com.kesh.web.WebHttpGateway;
import com.kesh.web.WebSearchClient;

@Configuration
public class KeshConfiguration {

    @Bean
    @Primary
    HttpClient keshHttpClient(KeshProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getLlm().getConnectTimeoutMs()))
                .build();
    }

    @Bean
    @Primary
    LlmClient llmClient(
            KeshProperties properties,
            HttpClient keshHttpClient,
            ObjectMapper objectMapper,
            com.kesh.llm.ModelRouter modelRouter,
            com.kesh.llm.ModelCatalog modelCatalog
    ) {
        return new com.kesh.llm.RoutedLlmClient(properties, modelRouter, modelCatalog, keshHttpClient, objectMapper);
    }

    @Bean
    HttpClient webRedirectSafeHttpClient(KeshProperties properties) {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(Math.max(2, properties.getWeb().getFetch().getTimeoutSeconds())))
                .build();
    }

    @Bean
    WebSearchClient webSearchClient(KeshProperties properties, WebHttpGateway gateway, ObjectMapper objectMapper) {
        String provider = properties.getWeb().getSearch().getProvider();
        String apiKey = properties.getWeb().getSearch().getApiKey();
        if (provider != null && "brave".equalsIgnoreCase(provider.trim()) && apiKey != null && !apiKey.isBlank()) {
            return new BraveSearchClient(gateway, objectMapper, apiKey.trim());
        }
        return new DuckDuckGoSearchClient(gateway);
    }

    @Bean
    LlmIntentClassifierFallback llmIntentClassifierFallback(
            LlmClient llmClient,
            KeshProperties properties,
            ObjectMapper objectMapper
    ) {
        return new LlmIntentClassifierFallback(llmClient, properties, objectMapper);
    }

    @Bean
    RemoteSetFitClassifier remoteSetFitClassifier(
            KeshProperties properties,
            ObjectMapper objectMapper,
            HttpClient keshHttpClient
    ) {
        return new RemoteSetFitClassifier(properties, objectMapper, keshHttpClient);
    }

    @Bean
    SystemPromptProvider systemPromptProvider() {
        return new SystemPromptProvider(new ClassPathResource("prompts/system-prompt.txt"));
    }
}
