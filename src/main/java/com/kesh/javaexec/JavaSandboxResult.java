package com.kesh.javaexec;

public record JavaSandboxResult(
        boolean success,
        String stage,
        int exitCode,
        String stdout,
        String stderr,
        long durationMs,
        String jdkVersion,
        String runtime
) {
}
