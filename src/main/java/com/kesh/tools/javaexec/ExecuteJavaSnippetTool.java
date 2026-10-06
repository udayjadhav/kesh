package com.kesh.tools.javaexec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.javaexec.JavaSandbox;
import com.kesh.javaexec.JavaSandboxResult;
import com.kesh.javaexec.JavaSnippetSourceGuard;
import com.kesh.javaexec.JavaSourcePreparer;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

/**
 * Policy-gated Java snippet runner. Gemma may request this; Spring executes it in a sandbox.
 */
@Component
@ConditionalOnProperty(prefix = "kesh.java-execution", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExecuteJavaSnippetTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ExecuteJavaSnippetTool.class);

    private final JavaSandbox sandbox;
    private final KeshProperties properties;
    private final Semaphore slots;

    public ExecuteJavaSnippetTool(JavaSandbox sandbox, KeshProperties properties) {
        this.sandbox = sandbox;
        this.properties = properties;
        this.slots = new Semaphore(Math.max(1, properties.getJavaExecution().getMaxConcurrentExecutions()));
    }

    @Override
    public String name() {
        return "execute_java_snippet";
    }

    @Override
    public String description() {
        return "Write and run a short Java snippet in an isolated container when an exact computed "
                + "result would help (math, ranking, sorting, regex, formatting). No network, no host files. "
                + "Requires user approval. Do not use this for mvn, git, shell, or to print Hello World.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.DANGEROUS;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of(
                        "source", new ToolSchema.Property("string", "Java source. A class with main, or a snippet."),
                        "timeoutMs", new ToolSchema.Property("integer", "Max runtime in milliseconds"),
                        "description", new ToolSchema.Property("string", "Short reason for running this snippet")
                ),
                List.of("source")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        String executionId = UUID.randomUUID().toString();
        var cfg = properties.getJavaExecution();
        try {
            if (!cfg.isEnabled()) {
                return ToolResult.fail(name(), "Java snippet execution is turned off.", elapsed(started));
            }
            String source = ToolArguments.requireString(arguments, "source");
            JavaSnippetSourceGuard.validate(source, cfg.getMaxSourceBytes());
            int timeoutMs = ToolArguments.optionalInt(arguments, "timeoutMs", cfg.getTimeoutMs());
            timeoutMs = Math.min(Math.max(timeoutMs, 250), 15_000);
            String main = JavaSourcePreparer.toMainClass(source);
            if (!slots.tryAcquire(1, TimeUnit.SECONDS)) {
                return ToolResult.fail(name(), "Too many Java snippets are running. Try again.", elapsed(started));
            }
            try {
                log.info("JAVA_EXEC id={} jdk={} runtime={} sourceHash={} timeoutMs={}",
                        executionId, cfg.getJdkVersion(), sandbox.runtimeName(), sha256(source), timeoutMs);
                JavaSandboxResult result = sandbox.execute(main, timeoutMs);
                return toToolResult(result, elapsed(started));
            } finally {
                slots.release();
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return ToolResult.fail(name(), e.getMessage(), elapsed(started));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.fail(name(), "Java snippet interrupted.", elapsed(started));
        }
    }

    private ToolResult toToolResult(JavaSandboxResult result, long duration) {
        String text = "stage=" + result.stage()
                + " exit=" + result.exitCode()
                + " jdk=" + result.jdkVersion()
                + "\nstdout:\n" + nullToEmpty(result.stdout())
                + "\nstderr:\n" + nullToEmpty(result.stderr());
        if (result.success()) {
            return ToolResult.ok(name(), text, duration);
        }
        return ToolResult.fail(name(), text, duration);
    }

    private static String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "unhashed";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
