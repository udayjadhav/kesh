package com.kesh.writing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.workspace.WorkspaceMode;

/**
 * Approved writing examples only. Never auto-trains on every generation.
 */
@Component
public class WritingDatasetStore {

    private static final Logger log = LoggerFactory.getLogger(WritingDatasetStore.class);

    private final KeshProperties properties;
    private final ObjectMapper objectMapper;

    public WritingDatasetStore(KeshProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void append(WorkspaceMode mode, WritingTurnStore.WritingTurn turn, boolean accepted, String comment) {
        if (turn == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("task", turn.task());
        row.put("context", turn.context());
        row.put("input", turn.input());
        row.put("output", turn.output());
        row.put("accepted", accepted);
        row.put("comment", comment == null ? "" : comment);
        row.put("desk", mode == null ? "work" : mode.path());
        row.put("timestamp", Instant.now().toString());
        try {
            Path path = Path.of(properties.getWorkspace().getDataDir(), "writing", mode == null ? "work" : mode.path(), "kesh-writing.jsonl");
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    objectMapper.writeValueAsString(row) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.info("Writing dataset skip: {}", e.getMessage());
        }
    }
}
