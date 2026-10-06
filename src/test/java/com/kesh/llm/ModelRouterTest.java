package com.kesh.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;
import com.kesh.nlp.Intent;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.Risk;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;

class ModelRouterTest {

    @Test
    void disabledRouterKeepsDefaultGemma() {
        KeshProperties properties = new KeshProperties();
        properties.getLlm().setModel("docker.io/ai/gemma3:latest");
        properties.getModelRouter().setEnabled(false);
        properties.getModelRouter().setCodeModel("qwen");
        ModelRouter router = new ModelRouter(properties);
        assertThat(router.modelFor(analysis(CapabilityIntent.LLM_CODE_HELP)))
                .isEqualTo("docker.io/ai/gemma3:latest");
        assertThat(router.judgeModel()).isEqualTo("docker.io/ai/gemma3:latest");
    }

    @Test
    void enabledRouterUsesCodeModelForCodeHelp() {
        KeshProperties properties = new KeshProperties();
        properties.getLlm().setModel("gemma");
        properties.getModelRouter().setEnabled(true);
        properties.getModelRouter().setCodeModel("qwen-coder");
        properties.getModelRouter().setChatModel("small-chat");
        properties.getModelRouter().setJudgeModel("judge-model");
        ModelRouter router = new ModelRouter(properties);
        assertThat(router.modelFor(analysis(CapabilityIntent.LLM_CODE_HELP))).isEqualTo("qwen-coder");
        assertThat(router.modelFor(analysis(CapabilityIntent.CHAT_GENERAL))).isEqualTo("small-chat");
        assertThat(router.modelFor(analysis(CapabilityIntent.WEB_SEARCH))).isEqualTo("small-chat");
        assertThat(router.judgeModel()).isEqualTo("judge-model");
        assertThat(router.decide(analysis(CapabilityIntent.LLM_DESIGN), "design a workzone router").role())
                .isEqualTo(ModelRole.REASONING_MODEL);
        assertThat(router.decide(analysis(CapabilityIntent.LLM_CODE_HELP), "fix this NPE").role())
                .isEqualTo(ModelRole.CODE_MODEL);
        assertThat(router.decide(analysis(CapabilityIntent.WRITING_SHORTEN), "make this shorter").role())
                .isEqualTo(ModelRole.WRITER_MODEL);
        assertThat(router.decide(analysis(CapabilityIntent.WRITING_DRAFT_EMAIL), "write a recruiter follow-up email").role())
                .isEqualTo(ModelRole.REASONING_MODEL);
    }

    @Test
    void catalogPicksFastAndReasoningFromLocalModels() {
        KeshProperties properties = new KeshProperties();
        properties.getLlm().setModel("docker.io/ai/gemma3:latest");
        properties.getModelRouter().setEnabled(true);
        properties.getModelRouter().setDiscover(false);
        KeshProperties.ModelSpec llama = new KeshProperties.ModelSpec();
        llama.setId("llama3.2");
        llama.setModel("llama3.2:latest");
        llama.setProvider("ollama");
        llama.setBaseUrl("http://127.0.0.1:11434");
        llama.setParameterSize("3.2B");
        llama.setTools(true);
        KeshProperties.ModelSpec gemma = new KeshProperties.ModelSpec();
        gemma.setId("gemma3");
        gemma.setModel("docker.io/ai/gemma3:latest");
        gemma.setProvider("docker-model-runner");
        gemma.setBaseUrl("http://127.0.0.1:12434");
        gemma.setParameterSize("3.88 B");
        properties.getModelRouter().getModels().add(llama);
        properties.getModelRouter().getModels().add(gemma);
        ModelCatalog catalog = new ModelCatalog(properties, new LocalModelDiscovery(
                java.net.http.HttpClient.newHttpClient(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties
        ));
        ModelRouter router = new ModelRouter(properties, catalog);
        assertThat(router.modelFor(analysis(CapabilityIntent.CHAT_GENERAL))).isEqualTo("llama3.2:latest");
        assertThat(router.modelFor(analysis(CapabilityIntent.LLM_DESIGN))).isEqualTo("docker.io/ai/gemma3:latest");
        assertThat(router.modelFor(analysis(CapabilityIntent.LLM_CODE_HELP))).isEqualTo("llama3.2:latest");
        assertThat(router.judgeModel()).isIn("llama3.2:latest", "docker.io/ai/gemma3:latest");
        assertThat(catalog.forRole(ModelRole.JUDGE_MODEL).model())
                .isNotEqualTo(catalog.forRole(ModelRole.FAST_MODEL).model());
    }

    private static RequestAnalysis analysis(CapabilityIntent intent) {
        return new RequestAnalysis(
                Intent.CHAT,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                true,
                Set.of(),
                false,
                "",
                ClassificationResult.single(intent, 0.9, java.util.Map.of(), "test"),
                null
        );
    }
}
