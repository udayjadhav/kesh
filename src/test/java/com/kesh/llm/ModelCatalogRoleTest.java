package com.kesh.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ModelCatalogRoleTest {

    @Test
    void infersFastCodeAndReasoningFromMetadata() {
        RegisteredModel llama = new RegisteredModel(
                "llama3.2",
                "llama3.2:latest",
                "ollama",
                "ollama",
                "http://127.0.0.1:11434",
                Set.of(),
                131072,
                0,
                "2.00 GiB",
                "3.2B",
                true,
                true,
                true
        );
        RegisteredModel gemma = new RegisteredModel(
                "gemma3",
                "docker.io/ai/gemma3:latest",
                "docker-model-runner",
                "docker-model-runner",
                "http://127.0.0.1:12434",
                Set.of(),
                0,
                0,
                "2.31 GiB",
                "3.88 B",
                false,
                true,
                true
        );
        assertThat(ModelCatalog.inferRoles(llama)).contains(ModelRole.FAST_MODEL, ModelRole.CODE_MODEL, ModelRole.TOOL_PLANNER_MODEL);
        assertThat(ModelCatalog.inferRoles(gemma)).contains(ModelRole.FAST_MODEL);
        assertThat(RequestComplexity.of(null, "hi")).isEqualTo(RequestComplexity.LOW);
        assertThat(RequestComplexity.of(null, "a".repeat(401))).isEqualTo(RequestComplexity.HIGH);
    }
}
