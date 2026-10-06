package com.kesh.os;

public record ProcessResult(int exitCode, String stdout, String stderr) {

    public boolean succeeded() {
        return exitCode == 0;
    }

    public String combinedOutput() {
        StringBuilder sb = new StringBuilder();
        if (stdout != null && !stdout.isBlank()) {
            sb.append(stdout.strip());
        }
        if (stderr != null && !stderr.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(stderr.strip());
        }
        return sb.toString();
    }
}
