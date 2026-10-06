package com.kesh.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

/**
 * Configured models plus whatever is actually running locally.
 */
@Component
public class ModelCatalog {

    private final KeshProperties properties;
    private final LocalModelDiscovery discovery;
    private volatile List<RegisteredModel> snapshot = List.of();

    public ModelCatalog(KeshProperties properties, LocalModelDiscovery discovery) {
        this.properties = properties;
        this.discovery = discovery;
        refresh();
    }

    public synchronized List<RegisteredModel> refresh() {
        List<RegisteredModel> merged = new ArrayList<>();
        Map<String, RegisteredModel> byKey = new LinkedHashMap<>();
        for (RegisteredModel discovered : discovery == null ? List.<RegisteredModel>of() : discovery.discover()) {
            byKey.put(key(discovered), discovered);
        }
        for (KeshProperties.ModelSpec spec : properties.getModelRouter().getModels()) {
            RegisteredModel configured = fromSpec(spec);
            if (configured == null) {
                continue;
            }
            byKey.put(key(configured), overlay(byKey.get(key(configured)), configured));
        }
        for (RegisteredModel model : byKey.values()) {
            if (model.enabled()) {
                merged.add(withRoles(model));
            }
        }
        snapshot = List.copyOf(fillMissingRoles(merged));
        return snapshot;
    }

    public List<RegisteredModel> models() {
        return snapshot;
    }

    public RegisteredModel find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (RegisteredModel model : snapshot) {
            if (model.matches(name)) {
                return model;
            }
        }
        return null;
    }

    public RegisteredModel forRole(ModelRole role) {
        if (role == null) {
            return null;
        }
        for (RegisteredModel model : snapshot) {
            if (model.enabled() && model.hasRole(role)) {
                return model;
            }
        }
        return firstEnabled();
    }

    public RegisteredModel firstEnabled() {
        for (RegisteredModel model : snapshot) {
            if (model.enabled()) {
                return model;
            }
        }
        return null;
    }

    public RegisteredModel fallback(RegisteredModel failed) {
        for (RegisteredModel model : snapshot) {
            if (model.enabled() && (failed == null || !model.matches(failed.model()))) {
                return model;
            }
        }
        return failed;
    }

    static Set<ModelRole> inferRoles(RegisteredModel model) {
        LinkedHashSet<ModelRole> roles = new LinkedHashSet<>();
        String hay = ((model.id() + " " + model.model() + " " + model.parameterSize()).toLowerCase(Locale.ROOT));
        double params = RegisteredModel.billionParams(model.parameterSize());
        if (hay.contains("coder") || hay.contains("codellama") || hay.contains("starcoder") || hay.contains("code-")) {
            roles.add(ModelRole.CODE_MODEL);
        }
        if (hay.contains("judge") || hay.contains("eval")) {
            roles.add(ModelRole.JUDGE_MODEL);
        }
        if (hay.contains("r1") || hay.contains("reason") || hay.contains("qwen") || hay.contains("deepseek")
                || params >= 8) {
            roles.add(ModelRole.REASONING_MODEL);
        }
        if (hay.contains("writer") || hay.contains("writing")) {
            roles.add(ModelRole.WRITER_MODEL);
        }
        if (hay.contains("mini") || hay.contains("tiny") || hay.contains("phi") || params > 0 && params <= 4.5
                || hay.contains("gemma") || hay.contains("llama3.2") || hay.contains("llama-3.2")) {
            roles.add(ModelRole.FAST_MODEL);
        }
        if (model.tools()) {
            roles.add(ModelRole.TOOL_PLANNER_MODEL);
            roles.add(ModelRole.CODE_MODEL);
        }
        if (roles.isEmpty()) {
            roles.add(ModelRole.FAST_MODEL);
        }
        return roles;
    }

    private RegisteredModel withRoles(RegisteredModel model) {
        if (model.roles() != null && !model.roles().isEmpty()) {
            return model;
        }
        return new RegisteredModel(
                model.id(),
                model.model(),
                model.provider(),
                model.runtime(),
                model.baseUrl(),
                inferRoles(model),
                model.contextSize(),
                model.expectedLatencyMs(),
                model.memory(),
                model.parameterSize(),
                model.tools(),
                model.enabled(),
                model.discovered()
        );
    }

    private static List<RegisteredModel> fillMissingRoles(List<RegisteredModel> models) {
        if (models.isEmpty()) {
            return models;
        }
        EnumMap<ModelRole, RegisteredModel> byRole = new EnumMap<>(ModelRole.class);
        for (RegisteredModel model : models) {
            for (ModelRole role : model.roles()) {
                byRole.putIfAbsent(role, model);
            }
        }
        RegisteredModel smallest = models.stream()
                .min(Comparator.comparingDouble(model -> {
                    double params = RegisteredModel.billionParams(model.parameterSize());
                    return params <= 0 ? 99 : params;
                }))
                .orElse(models.get(0));
        RegisteredModel largest = models.stream()
                .max(Comparator.comparingDouble(model -> RegisteredModel.billionParams(model.parameterSize())))
                .orElse(models.get(0));
        RegisteredModel second = models.size() > 1 && !models.get(1).matches(smallest.model())
                ? models.get(1)
                : (models.size() > 1 ? models.get(0) : smallest);
        if (models.size() > 1 && second.matches(smallest.model())) {
            second = models.stream().filter(model -> !model.matches(smallest.model())).findFirst().orElse(smallest);
        }
        byRole.putIfAbsent(ModelRole.FAST_MODEL, smallest);
        byRole.putIfAbsent(ModelRole.REASONING_MODEL, largest);
        byRole.putIfAbsent(ModelRole.CODE_MODEL, byRole.getOrDefault(ModelRole.TOOL_PLANNER_MODEL, largest));
        byRole.putIfAbsent(ModelRole.TOOL_PLANNER_MODEL, byRole.getOrDefault(ModelRole.CODE_MODEL, largest));
        byRole.putIfAbsent(ModelRole.REFINER_MODEL, second);
        byRole.putIfAbsent(ModelRole.JUDGE_MODEL, second);
        byRole.putIfAbsent(ModelRole.WRITER_MODEL, byRole.getOrDefault(ModelRole.FAST_MODEL, smallest));
        List<RegisteredModel> filled = new ArrayList<>();
        for (RegisteredModel model : models) {
            LinkedHashSet<ModelRole> roles = new LinkedHashSet<>(model.roles());
            for (Map.Entry<ModelRole, RegisteredModel> entry : byRole.entrySet()) {
                if (entry.getValue().matches(model.model())) {
                    roles.add(entry.getKey());
                }
            }
            filled.add(new RegisteredModel(
                    model.id(),
                    model.model(),
                    model.provider(),
                    model.runtime(),
                    model.baseUrl(),
                    roles,
                    model.contextSize(),
                    model.expectedLatencyMs(),
                    model.memory(),
                    model.parameterSize(),
                    model.tools(),
                    model.enabled(),
                    model.discovered()
            ));
        }
        return filled;
    }

    private RegisteredModel fromSpec(KeshProperties.ModelSpec spec) {
        if (spec == null || spec.getModel() == null && spec.getId() == null) {
            return null;
        }
        String model = first(spec.getModel(), spec.getId());
        LinkedHashSet<ModelRole> roles = new LinkedHashSet<>();
        if (spec.getRoles() != null) {
            for (String role : spec.getRoles()) {
                try {
                    roles.add(ModelRole.valueOf(role.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // skip unknown role names
                }
            }
        }
        String provider = first(spec.getProvider(), properties.getLlm().getProvider());
        String baseUrl = first(spec.getBaseUrl(), defaultBase(provider));
        return new RegisteredModel(
                first(spec.getId(), model),
                model,
                provider,
                first(spec.getRuntime(), provider),
                baseUrl,
                roles,
                spec.getContextSize(),
                spec.getExpectedLatencyMs(),
                spec.getMemory() == null ? "" : spec.getMemory(),
                spec.getParameterSize() == null ? "" : spec.getParameterSize(),
                spec.isTools(),
                spec.isEnabled(),
                false
        );
    }

    private RegisteredModel overlay(RegisteredModel discovered, RegisteredModel configured) {
        if (discovered == null) {
            return configured;
        }
        return new RegisteredModel(
                configured.id(),
                first(configured.model(), discovered.model()),
                first(configured.provider(), discovered.provider()),
                first(configured.runtime(), discovered.runtime()),
                first(configured.baseUrl(), discovered.baseUrl()),
                configured.roles().isEmpty() ? discovered.roles() : configured.roles(),
                configured.contextSize() > 0 ? configured.contextSize() : discovered.contextSize(),
                configured.expectedLatencyMs() > 0 ? configured.expectedLatencyMs() : discovered.expectedLatencyMs(),
                first(configured.memory(), discovered.memory()),
                first(configured.parameterSize(), discovered.parameterSize()),
                configured.tools() || discovered.tools(),
                configured.enabled(),
                discovered.discovered()
        );
    }

    private String defaultBase(String provider) {
        if (provider != null && provider.toLowerCase(Locale.ROOT).contains("ollama")) {
            return properties.getModelRouter().resolvedOllamaBaseUrl();
        }
        return properties.getModelRouter().resolvedDockerModelRunnerBaseUrl();
    }

    private static String key(RegisteredModel model) {
        return model.provider().toLowerCase(Locale.ROOT) + "|" + model.model().toLowerCase(Locale.ROOT);
    }

    private static String first(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return value.strip();
    }
}
