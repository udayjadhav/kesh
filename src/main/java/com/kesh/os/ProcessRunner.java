package com.kesh.os;

import java.time.Duration;
import java.util.List;

public interface ProcessRunner {

    ProcessResult run(List<String> command, Duration timeout);

    default ProcessResult run(List<String> command, Duration timeout, byte[] stdin) {
        return run(command, timeout);
    }
}
