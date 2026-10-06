package com.kesh.tools.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.security.PathAllowlist;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

@Component
public class ReadFileTool implements Tool {

    private final PathAllowlist allowlist;
    private final long maxReadBytes;

    public ReadFileTool(PathAllowlist allowlist, KeshProperties properties) {
        this.allowlist = allowlist;
        this.maxReadBytes = properties.getFilesystem().getMaxReadBytes();
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read a UTF-8 text file from an allowed directory. Rejects paths outside the allowlist.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("path", new ToolSchema.Property("string", "File path to read")),
                List.of("path")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        try {
            Path file = allowlist.resolveAllowed(ToolArguments.requireString(arguments, "path"));
            if (!Files.exists(file)) {
                return ToolResult.fail(name(), "File does not exist: " + file, elapsed(started));
            }
            if (!Files.isRegularFile(file)) {
                return ToolResult.fail(name(), "Not a regular file: " + file, elapsed(started));
            }
            long size = Files.size(file);
            if (size > maxReadBytes) {
                return ToolResult.fail(
                        name(),
                        "File is " + size + " bytes; max allowed is " + maxReadBytes,
                        elapsed(started)
                );
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return ToolResult.ok(name(), content, elapsed(started));
        } catch (SecurityException | IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        } catch (IOException e) {
            return ToolResult.fail(name(), "Failed to read file: " + e.getMessage(), elapsed(started));
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
