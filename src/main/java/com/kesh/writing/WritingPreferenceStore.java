package com.kesh.writing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.workspace.WorkspaceMode;

/**
 * User-specific writing preferences. Separate from conversation memory.
 */
@Component
public class WritingPreferenceStore {

    private static final Logger log = LoggerFactory.getLogger(WritingPreferenceStore.class);

    private final KeshProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Map<String, Double>> cache = new ConcurrentHashMap<>();

    public WritingPreferenceStore(KeshProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String hint(WorkspaceMode mode) {
        Map<String, Double> prefs = load(mode);
        if (prefs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        prefs.forEach((key, value) -> {
            if (value != null && value >= 0.6) {
                sb.append("- ").append(key.replace('_', ' ')).append('\n');
            }
        });
        return sb.toString();
    }

    public void learnFromFeedback(WorkspaceMode mode, String reason, String comment) {
        Map<String, Double> prefs = new LinkedHashMap<>(load(mode));
        apply(prefs, reason);
        apply(prefs, comment);
        cache.put(desk(mode), prefs);
        save(mode, prefs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> load(WorkspaceMode mode) {
        String desk = desk(mode);
        Map<String, Double> cached = cache.get(desk);
        if (cached != null) {
            return cached;
        }
        Path path = file(mode);
        if (!Files.isRegularFile(path)) {
            Map<String, Double> empty = new LinkedHashMap<>();
            cache.put(desk, empty);
            return empty;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(path.toFile(), Map.class);
            Map<String, Double> prefs = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (value instanceof Number number) {
                    prefs.put(key, number.doubleValue());
                }
            });
            cache.put(desk, prefs);
            return prefs;
        } catch (IOException e) {
            log.info("Writing preferences unread: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void save(WorkspaceMode mode, Map<String, Double> prefs) {
        try {
            Path path = file(mode);
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), prefs);
        } catch (IOException e) {
            log.info("Writing preferences skip: {}", e.getMessage());
        }
    }

    private Path file(WorkspaceMode mode) {
        return Path.of(properties.getWorkspace().getDataDir(), "writing", desk(mode), "preferences.json");
    }

    private static String desk(WorkspaceMode mode) {
        return mode == null ? "work" : mode.path();
    }

    private static void apply(Map<String, Double> prefs, String signal) {
        if (signal == null || signal.isBlank()) {
            return;
        }
        String lower = signal.toLowerCase(Locale.ROOT);
        bump(prefs, "concise", contains(lower, "too verbose", "too long", "make it shorter", "shorter"));
        bump(prefs, "casual", contains(lower, "too formal", "more casual"));
        bump(prefs, "professional", contains(lower, "more professional", "too casual"));
        bump(prefs, "natural", contains(lower, "too robotic", "more natural", "sound like me"));
        bump(prefs, "preserve_wording", contains(lower, "keep my wording", "changed my meaning", "minimal"));
        bump(prefs, "direct", contains(lower, "too polite", "more direct"));
    }

    private static boolean contains(String hay, String... needles) {
        for (String needle : needles) {
            if (hay.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void bump(Map<String, Double> prefs, String key, boolean hit) {
        if (!hit) {
            return;
        }
        prefs.put(key, Math.min(1.0, prefs.getOrDefault(key, 0.4) + 0.15));
    }
}
