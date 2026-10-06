package com.kesh.tools.system;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.kesh.os.ProcessResult;
import com.kesh.os.ProcessRunner;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

@Component
public class InspectRuntimeTool implements Tool {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("jdk", "java"),
            Map.entry("jre", "java"),
            Map.entry("nodejs", "node"),
            Map.entry("node.js", "node"),
            Map.entry("maven", "mvn"),
            Map.entry("golang", "go"),
            Map.entry("py", "python"),
            Map.entry("mac", "macos"),
            Map.entry("osx", "macos"),
            Map.entry("darwin", "macos"),
            Map.entry("os", "macos")
    );
    private static final Map<String, List<List<String>>> COMMANDS;

    static {
        Map<String, List<List<String>>> commands = new LinkedHashMap<>();
        commands.put("java", List.of(List.of("java", "-version")));
        commands.put("javac", List.of(List.of("javac", "-version")));
        commands.put("node", List.of(List.of("node", "-v")));
        commands.put("npm", List.of(List.of("npm", "-v")));
        commands.put("python", List.of(List.of("python3", "--version")));
        commands.put("python3", List.of(List.of("python3", "--version")));
        commands.put("mvn", List.of(List.of("mvn", "-version")));
        commands.put("git", List.of(List.of("git", "--version")));
        commands.put("docker", List.of(List.of("docker", "--version")));
        commands.put("go", List.of(List.of("go", "version")));
        commands.put("ruby", List.of(List.of("ruby", "-v")));
        commands.put("php", List.of(List.of("php", "-v")));
        commands.put("rustc", List.of(List.of("rustc", "--version")));
        commands.put("swift", List.of(List.of("swift", "--version")));
        commands.put("macos", List.of(List.of("sw_vers")));
        COMMANDS = Map.copyOf(commands);
    }

    private final ProcessRunner processRunner;

    public InspectRuntimeTool(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public String name() {
        return "inspect_runtime";
    }

    @Override
    public String description() {
        return "Read the installed version of a local toolchain or this Mac. "
                + "Use name java, node, python, git, mvn, docker, go, cursor, or macos. "
                + "Call this when the user asks what version is installed. Never guess the version.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("name", new ToolSchema.Property(
                        "string",
                        "What to check: java, node, python, git, mvn, docker, go, cursor, macos"
                )),
                List.of("name")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        String raw = ToolArguments.requireString(arguments, "name").toLowerCase(Locale.ROOT).trim();
        if (raw.startsWith("mac os")) {
            raw = "macos";
        }
        String key = ALIAS.getOrDefault(raw, raw);
        List<List<String>> probes = probesFor(key);
        if (probes.isEmpty()) {
            return ToolResult.fail(
                    name(),
                    "Unknown runtime '" + raw + "'. Try java, node, python, git, cursor, or macos.",
                    elapsed(started)
            );
        }
        List<String> tried = new ArrayList<>();
        ProcessResult last = null;
        for (List<String> command : probes) {
            tried.add(String.join(" ", command));
            ProcessResult result = processRunner.run(command, TIMEOUT);
            last = result;
            String output = result.combinedOutput();
            if (!missing(result, output)) {
                String body = output.isBlank() ? "(no output)" : output;
                return ToolResult.ok(
                        name(),
                        label(key) + " (" + String.join(" ", command) + "):\n" + body,
                        elapsed(started)
                );
            }
        }
        String detail = last == null || last.combinedOutput().isBlank()
                ? String.join("; ", tried)
                : last.combinedOutput();
        return ToolResult.ok(
                name(),
                label(key) + " is not installed (or not on PATH). Checked with: " + detail,
                elapsed(started)
        );
    }

    private static List<List<String>> probesFor(String key) {
        if ("cursor".equals(key)) {
            String home = System.getProperty("user.home");
            return List.of(
                    List.of("cursor", "--version"),
                    List.of(
                            "defaults", "read",
                            "/Applications/Cursor.app/Contents/Info.plist",
                            "CFBundleShortVersionString"
                    ),
                    List.of(
                            "defaults", "read",
                            home + "/Applications/Cursor.app/Contents/Info.plist",
                            "CFBundleShortVersionString"
                    )
            );
        }
        List<List<String>> known = COMMANDS.get(key);
        return known == null ? List.of() : known;
    }

    private static boolean missing(ProcessResult result, String output) {
        String text = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return text.contains("cannot run program")
                || text.contains("no such file")
                || text.contains("not found")
                || text.contains("does not exist")
                || (result.exitCode() != 0 && (text.isBlank() || text.contains("error domain")));
    }

    private static String label(String value) {
        if ("macos".equals(value)) {
            return "macOS";
        }
        if ("cursor".equals(value)) {
            return "Cursor";
        }
        if (value == null || value.isBlank()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
