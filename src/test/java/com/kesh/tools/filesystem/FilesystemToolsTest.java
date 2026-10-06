package com.kesh.tools.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kesh.config.KeshProperties;
import com.kesh.security.PathAllowlist;
import com.kesh.tools.ToolResult;

class FilesystemToolsTest {

    @TempDir
    Path temp;

    @Test
    void readFileReturnsContents() throws Exception {
        Path file = Files.writeString(temp.resolve("Hello.java"), "class Hello {}");
        ReadFileTool tool = new ReadFileTool(PathAllowlist.forPaths(List.of(temp.toString())), properties());
        ToolResult result = tool.execute(Map.of("path", file.toString()));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("class Hello");
    }

    @Test
    void readFileRejectsOutsidePath() throws Exception {
        Path allowed = Files.createDirectories(temp.resolve("ok"));
        Path outside = Files.writeString(temp.resolve("nope.txt"), "secret");
        ReadFileTool tool = new ReadFileTool(PathAllowlist.forPaths(List.of(allowed.toString())), properties());
        ToolResult result = tool.execute(Map.of("path", outside.toString()));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("outside");
    }

    @Test
    void searchFilesFindsJavaHits() throws Exception {
        Files.writeString(temp.resolve("UserService.java"), "KafkaConsumer consumer = new KafkaConsumer();");
        Files.writeString(temp.resolve("readme.txt"), "nope");
        SearchFilesTool tool = new SearchFilesTool(PathAllowlist.forPaths(List.of(temp.toString())), properties());
        ToolResult result = tool.execute(Map.of(
                "path", temp.toString(),
                "query", "KafkaConsumer",
                "filename_contains", ".java"
        ));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("UserService.java");
        assertThat(result.output()).doesNotContain("readme.txt");
    }

    @Test
    void listDirectoryShowsEntries() throws Exception {
        Files.writeString(temp.resolve("a.txt"), "a");
        ListDirectoryTool tool = new ListDirectoryTool(PathAllowlist.forPaths(List.of(temp.toString())), properties());
        ToolResult result = tool.execute(Map.of("path", temp.toString()));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("a.txt");
    }

    private static KeshProperties properties() {
        KeshProperties properties = new KeshProperties();
        properties.getFilesystem().setMaxReadBytes(1024 * 1024);
        properties.getFilesystem().setMaxSearchMatches(50);
        properties.getFilesystem().setMaxListEntries(200);
        return properties;
    }
}
