package com.kesh.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;

class CommandSafetyPolicyTest {

    private CommandSafetyPolicy policy;

    @BeforeEach
    void setUp() {
        KeshProperties properties = new KeshProperties();
        properties.getTerminal().setEnabled(true);
        properties.getTerminal().setAllowedCommands(List.of("git", "ls", "pwd", "mvn"));
        policy = new CommandSafetyPolicy(properties);
    }

    @Test
    void allowsGitStatus() {
        assertThat(policy.tokenizeAndValidate("git status")).containsExactly("git", "status");
    }

    @Test
    void rejectsRmRf() {
        assertThatThrownBy(() -> policy.tokenizeAndValidate("rm -rf /"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("rm -rf");
    }

    @Test
    void rejectsMetacharacters() {
        assertThatThrownBy(() -> policy.tokenizeAndValidate("ls | cat"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("metacharacters");
    }

    @Test
    void rejectsCommandNotOnAllowlist() {
        assertThatThrownBy(() -> policy.tokenizeAndValidate("shutdown now"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsGitPush() {
        assertThatThrownBy(() -> policy.tokenizeAndValidate("git push origin main"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("push");
    }

    @Test
    void disabledTerminalAlwaysRejects() {
        KeshProperties properties = new KeshProperties();
        properties.getTerminal().setEnabled(false);
        properties.getTerminal().setAllowedCommands(List.of("ls"));
        CommandSafetyPolicy disabled = new CommandSafetyPolicy(properties);
        assertThatThrownBy(() -> disabled.tokenizeAndValidate("ls"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("disabled");
    }
}
