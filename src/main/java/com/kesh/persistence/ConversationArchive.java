package com.kesh.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.workspace.WorkspaceMode;

@Component
public class ConversationArchive {

    private final Path root;
    private final ObjectMapper objectMapper;

    public ConversationArchive(KeshProperties properties, ObjectMapper objectMapper) {
        this.root = Path.of(properties.getWorkspace().getDataDir()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        migrateLegacyProfessionalFolder();
    }

    private void migrateLegacyProfessionalFolder() {
        Path legacy = root.resolve("professional");
        Path work = root.resolve("work");
        if (Files.exists(legacy) && !Files.exists(work)) {
            try {
                Files.move(legacy, work);
            } catch (IOException ignored) {
                // keep serving from professional only if move fails — fromPath still maps professional
            }
        }
    }

    public void save(StoredConversation conversation) {
        try {
            Path file = fileFor(conversation.mode(), conversation.id());
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), conversation);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save chat " + conversation.id(), e);
        }
    }

    public StoredConversation load(WorkspaceMode mode, String id) {
        Path file = fileFor(mode, id);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), StoredConversation.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read chat " + id, e);
        }
    }

    public List<StoredConversation> loadAll(WorkspaceMode mode) {
        Path dir = dirFor(mode);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try {
            List<StoredConversation> out = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted()
                        .forEach(path -> {
                            try {
                                out.add(objectMapper.readValue(path.toFile(), StoredConversation.class));
                            } catch (IOException ignored) {
                                // skip corrupt file
                            }
                        });
            }
            return List.copyOf(out);
        } catch (IOException e) {
            return List.of();
        }
    }

    public void delete(WorkspaceMode mode, String id) {
        try {
            Files.deleteIfExists(fileFor(mode, id));
        } catch (IOException ignored) {
            // eviction best-effort
        }
    }

    private Path dirFor(WorkspaceMode mode) {
        return root.resolve(mode.path()).resolve("conversations");
    }

    private Path fileFor(WorkspaceMode mode, String id) {
        return dirFor(mode).resolve(id + ".json");
    }

    public Path luceneDir(WorkspaceMode mode) {
        return root.resolve(mode.path()).resolve("lucene");
    }
}
