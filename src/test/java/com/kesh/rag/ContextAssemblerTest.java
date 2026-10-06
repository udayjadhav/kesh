package com.kesh.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;
import com.kesh.tools.ToolResult;

class ContextAssemblerTest {

    @Test
    void truncatesAndDeduplicates() {
        KeshProperties properties = new KeshProperties();
        properties.getNlp().getContext().setMaxTokens(20);
        properties.getNlp().getContext().setMaxDocuments(2);
        ContextAssembler assembler = new ContextAssembler(properties);
        List<RelatedSnippet> related = List.of(
                new RelatedSnippet("a", "one", "alpha ".repeat(80), 1.0f),
                new RelatedSnippet("a", "one", "alpha ".repeat(80), 0.9f),
                new RelatedSnippet("b", "two", "beta", 0.8f),
                new RelatedSnippet("c", "three", "gamma", 0.1f)
        );
        ContextAssembler.AssembledContext assembled = assembler.assemble("hello", related, List.of(
                ToolResult.ok("web_search", "Found sources https://example.com", 1)
        ));
        assertThat(assembled.chunks()).isNotEmpty();
        String block = assembler.promptBlock(assembled);
        assertThat(block).contains("RAG_RESULTS");
        assertThat(block).doesNotContain("USER_REQUEST");
    }

    @Test
    void emptyRelatedYieldsUserOnly() {
        ContextAssembler assembler = new ContextAssembler(new KeshProperties());
        ContextAssembler.AssembledContext assembled = assembler.assemble("hi", List.of(), List.of());
        assertThat(assembled.chunks()).hasSize(1);
        assertThat(assembler.promptBlock(assembled)).isBlank();
    }
}
