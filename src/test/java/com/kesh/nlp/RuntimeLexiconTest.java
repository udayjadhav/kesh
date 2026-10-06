package com.kesh.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuntimeLexiconTest {

    @Test
    void javaOnThisMacPrefersJava() {
        String text = "tell me what version of java installed on this mac";
        assertThat(RuntimeLexicon.isVersionAsk(text)).isTrue();
        assertThat(RuntimeLexicon.findCanonical(text)).isEqualTo("java");
    }

    @Test
    void cursorVersionAsk() {
        assertThat(RuntimeLexicon.isVersionAsk("which cursor version")).isTrue();
        assertThat(RuntimeLexicon.findCanonical("which cursor version")).isEqualTo("cursor");
    }

    @Test
    void macVersionAsk() {
        assertThat(RuntimeLexicon.isVersionAsk("which is mac version")).isTrue();
        assertThat(RuntimeLexicon.findCanonical("which is mac version")).isEqualTo("macos");
        assertThat(RuntimeLexicon.findCanonical("what macos version do I have")).isEqualTo("macos");
    }

    @Test
    void nodeAlias() {
        assertThat(RuntimeLexicon.findCanonical("what node.js version is installed")).isEqualTo("node");
        assertThat(RuntimeLexicon.isVersionAsk("what node version is installed?")).isTrue();
    }

    @Test
    void compareIsNotAVersionAsk() {
        assertThat(RuntimeLexicon.isVersionAsk("Okay, let’s compare Clojure and Java.")).isFalse();
        assertThat(RuntimeLexicon.isVersionAsk("hi")).isFalse();
        assertThat(RuntimeLexicon.isVersionAsk("why current time?")).isFalse();
        assertThat(RuntimeLexicon.isVersionAsk("run mvn test")).isFalse();
        assertThat(RuntimeLexicon.isVersionAsk("What's the latest Java version?")).isFalse();
    }

    @Test
    void compareIsNotAShellAsk() {
        assertThat(CommandLexicon.isShellAsk("Okay, let’s compare Clojure and Java. Use a markdown table.")).isFalse();
        assertThat(CommandLexicon.isShellAsk("run mvn test")).isTrue();
        assertThat(CommandLexicon.isShellAsk("java -version")).isFalse();
        assertThat(CommandLexicon.isImperativeShellAsk("Run this Java code and tell me what it prints")).isFalse();
        assertThat(CommandLexicon.isImperativeShellAsk("run mvn test")).isTrue();
    }
}
