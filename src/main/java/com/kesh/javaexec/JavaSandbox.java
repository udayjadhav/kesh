package com.kesh.javaexec;

public interface JavaSandbox {

    boolean available();

    String runtimeName();

    JavaSandboxResult execute(String mainSource, int timeoutMs);
}
