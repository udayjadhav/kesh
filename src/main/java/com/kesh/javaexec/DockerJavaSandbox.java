package com.kesh.javaexec;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.os.ProcessResult;
import com.kesh.os.ProcessRunner;

/**
 * Disposable Docker/Podman JVM. No host filesystem, no network, no secrets.
 */
@Component
public class DockerJavaSandbox implements JavaSandbox {

    private static final Logger log = LoggerFactory.getLogger(DockerJavaSandbox.class);
    private static final String SCRIPT = """
            set -eu
            cat > /work/Main.java
            if ! javac /work/Main.java; then
              echo '__KESH_STAGE=COMPILE' >&2
              exit 1
            fi
            echo '__KESH_STAGE=EXECUTION' >&2
            java -Xmx128m -cp /work Main
            """;

    private final ProcessRunner processRunner;
    private final KeshProperties properties;
    private volatile String resolvedRuntime;

    public DockerJavaSandbox(ProcessRunner processRunner, KeshProperties properties) {
        this.processRunner = processRunner;
        this.properties = properties;
    }

    @Override
    public boolean available() {
        return runtime() != null;
    }

    @Override
    public String runtimeName() {
        return runtime() == null ? "unavailable" : runtime();
    }

    @Override
    public JavaSandboxResult execute(String mainSource, int timeoutMs) {
        long started = System.nanoTime();
        var cfg = properties.getJavaExecution();
        String runtime = runtime();
        if (runtime == null) {
            return new JavaSandboxResult(
                    false, "SANDBOX", 127, "",
                    "Docker or Podman is required for Java snippets. Host JVM execution is not allowed.",
                    elapsed(started), cfg.getJdkVersion(), "unavailable"
            );
        }
        int runTimeout = Math.max(500, timeoutMs);
        Duration wall = Duration.ofMillis(runTimeout + Math.max(1_000, cfg.getStartGraceMs()));
        List<String> command = dockerCommand(cfg);
        ProcessResult result = processRunner.run(command, wall, mainSource.getBytes(StandardCharsets.UTF_8));
        long duration = elapsed(started);
        String stdout = clip(result.stdout(), cfg.getMaxOutputBytes());
        String stderr = clip(result.stderr(), cfg.getMaxOutputBytes());
        String stage = stageOf(stderr, result.exitCode());
        boolean success = result.exitCode() == 0;
        log.info("JAVA_SANDBOX runtime={} jdk={} stage={} exit={} durationMs={}",
                runtime, cfg.getJdkVersion(), stage, result.exitCode(), duration);
        return new JavaSandboxResult(
                success,
                stage,
                result.exitCode(),
                stdout,
                stripStage(stderr),
                duration,
                cfg.getJdkVersion(),
                runtime
        );
    }

    private List<String> dockerCommand(KeshProperties.JavaExecution cfg) {
        List<String> command = new ArrayList<>();
        command.add(runtime());
        command.add("run");
        command.add("--rm");
        command.add("-i");
        command.add("--network=none");
        command.add("--memory=" + cfg.getMemoryLimit());
        command.add("--cpus=" + cfg.getCpuLimit());
        command.add("--pids-limit=64");
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/work:rw,exec,nosuid,size=32m");
        command.add("--tmpfs");
        command.add("/tmp:rw,noexec,nosuid,size=16m");
        command.add("--user");
        command.add("65534:65534");
        command.add("--workdir");
        command.add("/work");
        command.add("--security-opt");
        command.add("no-new-privileges");
        command.add("--cap-drop");
        command.add("ALL");
        command.add(cfg.getImage());
        command.add("sh");
        command.add("-c");
        command.add(SCRIPT);
        return command;
    }

    private String runtime() {
        String current = resolvedRuntime;
        if (current != null) {
            return current.isBlank() ? null : current;
        }
        synchronized (this) {
            if (resolvedRuntime == null) {
                String found = resolveRuntime(properties.getJavaExecution().getRuntime(), processRunner);
                resolvedRuntime = found == null ? "" : found;
            }
        }
        return resolvedRuntime.isBlank() ? null : resolvedRuntime;
    }

    private static String resolveRuntime(String configured, ProcessRunner processRunner) {
        if (configured != null && !"auto".equalsIgnoreCase(configured.trim())) {
            return configured.trim();
        }
        if (hasBinary(processRunner, "docker")) {
            return "docker";
        }
        if (hasBinary(processRunner, "podman")) {
            return "podman";
        }
        return null;
    }

    private static boolean hasBinary(ProcessRunner processRunner, String name) {
        ProcessResult result = processRunner.run(List.of(name, "--version"), Duration.ofSeconds(3));
        return result.exitCode() == 0;
    }

    private static String stageOf(String stderr, int exitCode) {
        if (stderr != null && stderr.contains("__KESH_STAGE=COMPILE")) {
            return "COMPILE";
        }
        if (stderr != null && stderr.contains("__KESH_STAGE=EXECUTION")) {
            return "EXECUTION";
        }
        return exitCode == 0 ? "EXECUTION" : "SANDBOX";
    }

    private static String stripStage(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "";
        }
        return stderr.replace("__KESH_STAGE=COMPILE", "")
                .replace("__KESH_STAGE=EXECUTION", "")
                .strip();
    }

    private static String clip(String text, int maxBytes) {
        if (text == null) {
            return "";
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8) + "\n…truncated…";
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
