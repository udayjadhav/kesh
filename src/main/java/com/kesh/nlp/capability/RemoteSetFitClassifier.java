package com.kesh.nlp.capability;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;

/**
 * Optional HTTP client for a local SetFit/Python sidecar. Unused unless type=setfit.
 */
public class RemoteSetFitClassifier implements CapabilityClassifier {

    private static final Logger log = LoggerFactory.getLogger(RemoteSetFitClassifier.class);

    private final KeshProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RemoteSetFitClassifier(KeshProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String name() {
        return "setfit";
    }

    @Override
    public ClassificationResult classify(String text, DeskContext context) {
        String endpoint = properties.getNlp().getClassifier().getEndpoint();
        if (endpoint == null || endpoint.isBlank() || text == null) {
            throw new IllegalStateException("SetFit endpoint is not configured");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of("text", text));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.replaceAll("/$", "") + "/classify"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("SetFit HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<ClassifiedIntent> intents = new ArrayList<>();
            JsonNode array = root.get("intents");
            if (array != null && array.isArray()) {
                for (JsonNode item : array) {
                    CapabilityIntent intent = CapabilityIntent.parse(item.path("intent").asText());
                    intents.add(new ClassifiedIntent(
                            intent.domain(),
                            intent,
                            item.path("confidence").asDouble(0.5),
                            OpenNlpCapabilityClassifier.entitiesFor(text, intent)
                    ));
                }
            } else {
                CapabilityIntent intent = CapabilityIntent.parse(root.path("intent").asText());
                intents.add(new ClassifiedIntent(
                        intent.domain(),
                        intent,
                        root.path("confidence").asDouble(0.5),
                        OpenNlpCapabilityClassifier.entitiesFor(text, intent)
                ));
            }
            return new ClassificationResult(intents, name(), false, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SetFit classify interrupted", e);
        } catch (Exception e) {
            log.info("SetFit sidecar unavailable: {}", e.getMessage());
            throw new IllegalStateException("SetFit classify failed", e);
        }
    }
}
