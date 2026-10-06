package com.kesh.nlp.capability;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class CapabilityTrainingData {

    private final List<CapabilityExample> train;
    private final List<CapabilityExample> eval;

    public CapabilityTrainingData(
            ObjectMapper objectMapper,
            @Value("classpath:nlp/kesh-intents.jsonl") Resource trainResource,
            @Value("classpath:nlp/evaluation.jsonl") Resource evalResource
    ) {
        this.train = load(objectMapper, trainResource);
        this.eval = load(objectMapper, evalResource);
    }

    public List<CapabilityExample> train() {
        return train;
    }

    public List<CapabilityExample> eval() {
        return eval;
    }

    static List<CapabilityExample> load(ObjectMapper mapper, Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        )) {
            List<CapabilityExample> examples = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                examples.add(parse(mapper.readTree(line)));
            }
            if (examples.isEmpty()) {
                throw new IllegalStateException("No capability examples in " + resource.getFilename());
            }
            return List.copyOf(examples);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + resource, e);
        }
    }

    private static CapabilityExample parse(JsonNode node) {
        String text = text(node, "text");
        CapabilityIntent intent = CapabilityIntent.parse(text(node, "intent"));
        CapabilityDomain domain = node.hasNonNull("domain")
                ? CapabilityDomain.valueOf(node.get("domain").asText().trim().toUpperCase())
                : intent.domain();
        Map<String, Object> entities = new LinkedHashMap<>();
        JsonNode entityNode = node.get("entities");
        if (entityNode != null && entityNode.isObject()) {
            entityNode.fields().forEachRemaining(entry -> entities.put(entry.getKey(), asJava(entry.getValue())));
        }
        List<CapabilityIntent> extra = new ArrayList<>();
        JsonNode intents = node.get("intents");
        if (intents != null && intents.isArray()) {
            for (JsonNode item : intents) {
                CapabilityIntent parsed = item.isTextual()
                        ? CapabilityIntent.parse(item.asText())
                        : CapabilityIntent.parse(text(item, "intent"));
                if (parsed != intent) {
                    extra.add(parsed);
                }
            }
        }
        return new CapabilityExample(text, intent, domain, entities, extra);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static Object asJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }
}
