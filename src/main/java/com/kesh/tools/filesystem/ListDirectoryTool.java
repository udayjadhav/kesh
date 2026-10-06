package com.kesh.tools.filesystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
public class ListDirectoryTool implements Tool {

    private final PathAllowlist allowlist;
    private final int maxEntries;

    public ListDirectoryTool(PathAllowlist allowlist, KeshProperties properties) {
        this.allowlist = allowlist;
        this.maxEntries = properties.getFilesystem().getMaxListEntries();
    }

    @Override
    public String name() {
        return "list_directory";
    }

    @Override
    public String description() {
        return "List files and folders in an allowed directory. Path must be inside the configured filesystem allowlist.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("path", new ToolSchema.Property("string", "Directory to list")),
                List.of("path")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        try {
            Path dir = allowlist.resolveAllowed(ToolArguments.requireString(arguments, "path"));
            if (!Files.exists(dir)) {
                return ToolResult.fail(name(), "Directory does not exist: " + dir, elapsed(started));
            }
            if (!Files.isDirectory(dir)) {
                return ToolResult.fail(name(), "Not a directory: " + dir, elapsed(started));
            }
            List<String> lines = new ArrayList<>();
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path child : stream) {
                    if (count >= maxEntries) {
                        lines.add("... truncated after " + maxEntries + " entries");
                        break;
                    }
                    String type = Files.isDirectory(child) ? "dir" : "file";
                    lines.add(type + "\t" + child.getFileName());
                    count++;
                }
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("path", dir.toString());
            payload.put("entries", lines);
            return ToolResult.ok(name(), String.join("\n", lines), elapsed(started));
        } catch (SecurityException | IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        } catch (IOException e) {
            return ToolResult.fail(name(), "Failed to list directory: " + e.getMessage(), elapsed(started));
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
