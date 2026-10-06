package com.kesh.os;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

@Component
public class DefaultProcessRunner implements ProcessRunner {

    @Override
    public ProcessResult run(List<String> command, Duration timeout) {
        return run(command, timeout, null);
    }

    @Override
    public ProcessResult run(List<String> command, Duration timeout, byte[] stdin) {
        ProcessBuilder builder = new ProcessBuilder(command);
        enrichPath(builder.environment());
        try {
            Process process = builder.start();
            if (stdin != null) {
                try (var out = process.getOutputStream()) {
                    out.write(stdin);
                }
            } else {
                process.getOutputStream().close();
            }
            StreamDrain stdout = StreamDrain.start(process.getInputStream());
            StreamDrain stderr = StreamDrain.start(process.getErrorStream());
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdout.joinQuietly();
                stderr.joinQuietly();
                return new ProcessResult(124, stdout.text(), "Command timed out after " + timeout.toSeconds() + "s");
            }
            stdout.joinQuietly();
            stderr.joinQuietly();
            return new ProcessResult(process.exitValue(), stdout.text(), stderr.text());
        } catch (IOException e) {
            return new ProcessResult(1, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessResult(1, "", "Command interrupted");
        }
    }

    private static void enrichPath(Map<String, String> env) {
        String current = env.getOrDefault("PATH", "");
        String extra = "/opt/homebrew/bin:/opt/homebrew/sbin:/opt/homebrew/opt/openjdk@21/bin"
                + ":/Applications/Cursor.app/Contents/Resources/app/bin:/usr/local/bin:/usr/bin:/bin";
        Path nvmBin = newestNvmBin();
        if (nvmBin != null) {
            extra = nvmBin + ":" + extra;
        }
        env.put("PATH", extra + (current.isBlank() ? "" : ":" + current));
    }

    private static Path newestNvmBin() {
        Path versions = Path.of(System.getProperty("user.home"), ".nvm", "versions", "node");
        if (!Files.isDirectory(versions)) {
            return null;
        }
        try (var stream = Files.list(versions)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("bin"))
                    .filter(Files::isDirectory)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static final class StreamDrain implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final Thread thread;
        private volatile Exception error;

        private StreamDrain(InputStream input) {
            this.input = input;
            this.thread = new Thread(this, "kesh-process-drain");
            this.thread.setDaemon(true);
        }

        static StreamDrain start(InputStream input) {
            StreamDrain drain = new StreamDrain(input);
            drain.thread.start();
            return drain;
        }

        @Override
        public void run() {
            try {
                input.transferTo(buffer);
            } catch (IOException e) {
                error = e;
            }
        }

        void joinQuietly() {
            try {
                thread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String text() {
            if (error != null) {
                return error.getMessage() == null ? "" : error.getMessage();
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
