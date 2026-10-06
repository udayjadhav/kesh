package com.kesh.javaexec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JavaSnippetSourceGuardTest {

    @Test
    void allowsArithmetic() {
        JavaSnippetSourceGuard.validate("System.out.println(2 + 2);", 16_384);
    }

    @Test
    void rejectsProcessBuilder() {
        assertThatThrownBy(() -> JavaSnippetSourceGuard.validate(
                "new ProcessBuilder(\"ls\").start();",
                16_384
        )).isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsNetwork() {
        assertThatThrownBy(() -> JavaSnippetSourceGuard.validate(
                "java.net.Socket s = new java.net.Socket(\"example.com\", 80);",
                16_384
        )).isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsHomeDirectory() {
        assertThatThrownBy(() -> JavaSnippetSourceGuard.validate(
                "new File(\"/Users/uday/.ssh\");",
                16_384
        )).isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsEnvironment() {
        assertThatThrownBy(() -> JavaSnippetSourceGuard.validate(
                "System.getenv(\"AWS_SECRET_ACCESS_KEY\");",
                16_384
        )).isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsOversizedSource() {
        assertThatThrownBy(() -> JavaSnippetSourceGuard.validate("x".repeat(100), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrapSnippetAddsMain() {
        String source = JavaSourcePreparer.toMainClass("System.out.println(2);");
        assertThat(source).contains("class Main");
        assertThat(source).contains("public static void main");
        assertThat(source).contains("System.out.println(2);");
    }
}
