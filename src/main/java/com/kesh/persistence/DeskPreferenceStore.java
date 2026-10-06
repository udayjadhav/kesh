package com.kesh.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.workspace.WorkspaceMode;

@Component
public class DeskPreferenceStore {

    private final Path root;
    private final ObjectMapper objectMapper;

    public DeskPreferenceStore(KeshProperties properties, ObjectMapper objectMapper) {
        this.root = Path.of(properties.getWorkspace().getDataDir()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public String note(WorkspaceMode mode) {
        Path file = fileFor(mode);
        if (!Files.exists(file)) {
            return "";
        }
        try {
            Map<?, ?> json = objectMapper.readValue(file.toFile(), Map.class);
            Object note = json.get("note");
            return note == null ? "" : note.toString();
        } catch (IOException e) {
            return "";
        }
    }

    public void save(WorkspaceMode mode, String note) {
        try {
            Path file = fileFor(mode);
            Files.createDirectories(file.getParent());
            Map<String, String> body = new LinkedHashMap<>();
            body.put("note", note == null ? "" : note);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), body);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save preference for " + mode.path(), e);
        }
    }

    private Path fileFor(WorkspaceMode mode) {
        return root.resolve(mode.path()).resolve("preference.json");
    }
}
