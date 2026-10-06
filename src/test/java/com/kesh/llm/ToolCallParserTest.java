package com.kesh.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.tools.ToolCall;

class ToolCallParserTest {

    private final ToolCallParser parser = new ToolCallParser(new ObjectMapper());

    @Test
    void parsesTaggedBlock() {
        List<ToolCall> calls = parser.parse("""
                <tool_call>
                {"tool":"read_file","arguments":{"path":"/tmp/a.java"}}
                </tool_call>
                """);
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).tool()).isEqualTo("read_file");
        assertThat(calls.get(0).arguments()).containsEntry("path", "/tmp/a.java");
    }

    @Test
    void parsesFencedJson() {
        List<ToolCall> calls = parser.parse("""
                ```json
                {"tool":"list_directory","arguments":{"path":"/tmp"}}
                ```
                """);
        assertThat(calls).extracting(ToolCall::tool).containsExactly("list_directory");
    }

    @Test
    void malformedJsonIsEmpty() {
        assertThat(parser.parse("{\"tool\": ")).isEmpty();
        assertThat(parser.looksLikeToolCall("{\"tool\": ")).isTrue();
    }

    @Test
    void parsesEmptyArgumentsObject() {
        List<ToolCall> calls = parser.parse("""
                <tool_call>
                {"tool":"list_running_apps","arguments":{}}
                </tool_call>
                """);
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).tool()).isEqualTo("list_running_apps");
        assertThat(calls.get(0).arguments()).isEmpty();
    }

    @Test
    void gemmaWebSearchXmlIsNotAToolCallAndIsStripped() {
        String leak = """
                <web_search>
                {"query": "AI system design", "max_results": 5}
                </web_search>
                """;
        assertThat(parser.parse(leak)).isEmpty();
        assertThat(parser.looksLikeInvalidToolProse(leak)).isTrue();
        assertThat(parser.looksLikeToolCall(leak)).isTrue();
        assertThat(parser.stripToolMarkup(leak)).doesNotContain("web_search");
        assertThat(parser.stripToolMarkup(leak)).doesNotContain("AI system design");
    }
}
