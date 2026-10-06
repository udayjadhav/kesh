package com.kesh.tools.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
public class SearchFilesTool implements Tool {

    private final PathAllowlist allowlist;
    private final int maxMatches;

    public SearchFilesTool(PathAllowlist allowlist, KeshProperties properties) {
        this.allowlist = allowlist;
        this.maxMatches = properties.getFilesystem().getMaxSearchMatches();
    }

    @Override
    public String name() {
        return "search_files";
    }

    @Override
    public String description() {
        return "Search file contents under an allowed directory. Optional glob-style filename contains filter, for example .java";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(Map.of(
                "path", new ToolSchema.Property("string", "Directory to search"),
                "query", new ToolSchema.Property("string", "Text to find"),
                "filename_contains", new ToolSchema.Property("string", "Optional filename substring filter, e.g. .java")
        ), List.of("path", "query"));
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        try {
            Path root = allowlist.resolveAllowed(ToolArguments.requireString(arguments, "path"));
            String query = ToolArguments.requireString(arguments, "query");
            String filenameContains = ToolArguments.optionalString(arguments, "filename_contains", "");
            if (!Files.exists(root)) {
                return ToolResult.fail(name(), "Path does not exist: " + root, elapsed(started));
            }
            Path searchRoot = Files.isDirectory(root) ? root : root.getParent();
            if (searchRoot == null) {
                return ToolResult.fail(name(), "Cannot search " + root, elapsed(started));
            }
            String needle = query.toLowerCase(Locale.ROOT);
            String nameFilter = filenameContains.toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (name.equals(".git") || name.equals("target") || name.equals("node_modules") || name.equals(".idea")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matches.size() >= maxMatches) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!attrs.isRegularFile() || attrs.size() > 1_048_576) {
                        return FileVisitResult.CONTINUE;
                    }
                    String filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!nameFilter.isBlank() && !filename.contains(nameFilter)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        allowlist.resolveAllowed(file.toString());
                    } catch (SecurityException e) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            if (lines.get(i).toLowerCase(Locale.ROOT).contains(needle)) {
                                matches.add(file + ":" + (i + 1) + ":" + lines.get(i).strip());
                                if (matches.size() >= maxMatches) {
                                    break;
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        return FileVisitResult.CONTINUE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (matches.isEmpty()) {
                return ToolResult.ok(name(), "No matches for '" + query + "' under " + searchRoot, elapsed(started));
            }
            String header = matches.size() >= maxMatches
                    ? "Showing first " + maxMatches + " matches:\n"
                    : matches.size() + " matches:\n";
            return ToolResult.ok(name(), header + String.join("\n", matches), elapsed(started));
        } catch (SecurityException | IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        } catch (IOException e) {
            return ToolResult.fail(name(), "Search failed: " + e.getMessage(), elapsed(started));
        }
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
