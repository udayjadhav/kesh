package com.kesh.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kesh.config.KeshProperties;

class PathAllowlistTest {

    @TempDir
    Path temp;

    @Test
    void allowsPathInsideConfiguredRoot() throws IOException {
        Path allowed = temp.resolve("Projects");
        Files.createDirectories(allowed);
        Path file = Files.writeString(allowed.resolve("notes.txt"), "hi");
        PathAllowlist allowlist = PathAllowlist.forPaths(List.of(allowed.toString()));
        assertThat(allowlist.resolveAllowed(file.toString())).isEqualTo(file.toRealPath());
    }

    @Test
    void rejectsPathOutsideConfiguredRoot() throws IOException {
        Path allowed = Files.createDirectories(temp.resolve("Projects"));
        Path outside = Files.writeString(temp.resolve("secret.txt"), "nope");
        PathAllowlist allowlist = PathAllowlist.forPaths(List.of(allowed.toString()));
        assertThatThrownBy(() -> allowlist.resolveAllowed(outside.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void rejectsParentTraversal() throws IOException {
        Path allowed = Files.createDirectories(temp.resolve("Projects"));
        PathAllowlist allowlist = PathAllowlist.forPaths(List.of(allowed.toString()));
        assertThatThrownBy(() -> allowlist.resolveAllowed(allowed.resolve("../secret.txt").toString()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsSymlinkEscape() throws IOException {
        Path allowed = Files.createDirectories(temp.resolve("Projects"));
        Path outside = Files.writeString(temp.resolve("outside.txt"), "escaped");
        Path link = allowed.resolve("link.txt");
        Files.createSymbolicLink(link, outside);
        PathAllowlist allowlist = PathAllowlist.forPaths(List.of(allowed.toString()));
        assertThatThrownBy(() -> allowlist.resolveAllowed(link.toString()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void personalDeskCannotReadWorkRoot() throws IOException {
        Path work = Files.createDirectories(temp.resolve("work-root"));
        Path personal = Files.createDirectories(temp.resolve("personal-root"));
        Path workFile = Files.writeString(work.resolve("ticket.txt"), "jira");
        Path personalFile = Files.writeString(personal.resolve("notes.txt"), "leetcode");
        KeshProperties properties = new KeshProperties();
        properties.getFilesystem().setAllowedPaths(List.of(work.toString()));
        properties.getFilesystem().setPersonalPaths(List.of(personal.toString()));
        PathAllowlist allowlist = new PathAllowlist(properties);
        try {
            com.kesh.workspace.Workspace.set(com.kesh.workspace.WorkspaceMode.PROFESSIONAL);
            assertThat(allowlist.resolveAllowed(workFile.toString())).isEqualTo(workFile.toRealPath());
            assertThatThrownBy(() -> allowlist.resolveAllowed(personalFile.toString()))
                    .isInstanceOf(SecurityException.class);
            com.kesh.workspace.Workspace.set(com.kesh.workspace.WorkspaceMode.PERSONAL);
            assertThat(allowlist.resolveAllowed(personalFile.toString())).isEqualTo(personalFile.toRealPath());
            assertThatThrownBy(() -> allowlist.resolveAllowed(workFile.toString()))
                    .isInstanceOf(SecurityException.class);
        } finally {
            com.kesh.workspace.Workspace.clear();
        }
    }
}
