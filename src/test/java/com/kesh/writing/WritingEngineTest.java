package com.kesh.writing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.agent.AgentEventListener;
import com.kesh.agent.AgentResponse;
import com.kesh.agent.ConversationContext;
import com.kesh.config.KeshProperties;
import com.kesh.llm.LlmResponse;
import com.kesh.llm.ModelRouter;
import com.kesh.llm.ScriptedLlmClient;
import com.kesh.nlp.Intent;
import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.Risk;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.workspace.WorkspaceMode;

class WritingEngineTest {

    @Test
    void grammarPathDoesNotCallTheModel() {
        ScriptedLlmClient llm = new ScriptedLlmClient();
        WritingEngine engine = engine(llm);
        AgentResponse response = engine.handle(
                context(),
                analysis(CapabilityIntent.WRITING_CORRECT_GRAMMAR),
                "fix grammar:\nI wanted to check if there is any updates",
                AgentEventListener.noop(),
                "i-1"
        );
        assertThat(response.message().toLowerCase()).contains("update");
        assertThat(response.writing().get("mode")).isEqualTo("CORRECTION");
        assertThat(response.writing().get("original")).isEqualTo("I wanted to check if there is any updates");
    }

    @Test
    void professionalRewriteUsesLocalModelThenLanguageTool() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("Hi, just checking if there are any updates regarding the role."));
        WritingEngine engine = engine(llm);
        AgentResponse response = engine.handle(
                context(),
                analysis(CapabilityIntent.WRITING_PROFESSIONAL),
                "make this professional:\nhey, any update on the role?",
                AgentEventListener.noop(),
                "i-2"
        );
        assertThat(response.writing().get("mode")).isEqualTo("REWRITE");
        assertThat(response.message().toLowerCase()).contains("update");
        assertThat(response.message()).doesNotContain("aforementioned");
    }

    @Test
    void slackDraftIsNotAnEmail() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("Any update on the deploy?"));
        WritingEngine engine = engine(llm);
        AgentResponse response = engine.handle(
                context(),
                analysis(CapabilityIntent.WRITING_DRAFT_SLACK),
                "make this Slack-friendly:\nI would like to request an update on the deployment status at your earliest convenience.",
                AgentEventListener.noop(),
                "i-3"
        );
        assertThat(response.writing().get("channel")).isEqualTo("SLACK");
        assertThat(response.message().toLowerCase()).doesNotContain("dear");
        assertThat(response.message().toLowerCase()).doesNotContain("subject:");
    }

    @Test
    void formatNormalizesFragmentsWithoutInventingFacts() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("Hi Team,\n\nNot feeling well. Taking the day off today."));
        WritingEngine engine = engine(llm);
        AgentResponse response = engine.handle(
                context(),
                analysis(CapabilityIntent.WRITING_FORMAT),
                "Hi Team ,\nNot feelin well , day off\n\nformat this message",
                AgentEventListener.noop(),
                "i-4"
        );
        assertThat(response.writing().get("mode")).isEqualTo("FORMAT");
        assertThat(response.message()).contains("Not feeling well");
        assertThat(response.message()).contains("day off");
        assertThat(response.message().toLowerCase()).doesNotContain("here is the formatted");
        assertThat(response.message().toLowerCase()).doesNotContain("i'll be back tomorrow");
        assertThat(response.message().toLowerCase()).doesNotContain("tomorrow");
    }

    @Test
    void summarizeFollowUpKeepsCurrently() {
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .then(LlmResponse.text("Traffic is currently heavy between Wadgaon Sheri and Yerwada."));
        WritingEngine engine = engine(llm);
        ConversationContext ctx = context();
        ctx.addUser("traffic in Pune between Wadgaon Sheri to Yerwada now");
        ctx.addAssistant("Traffic between Wadgaon Sheri and Yerwada is currently heavy.");
        AgentResponse response = engine.handle(
                ctx,
                analysis(CapabilityIntent.WRITING_SUMMARIZE),
                "can you summarize above response",
                AgentEventListener.noop(),
                "i-5"
        );
        assertThat(response.message().toLowerCase()).contains("currently");
        assertThat(response.message().toLowerCase()).contains("wadgaon");
    }

    private static WritingEngine engine(ScriptedLlmClient llm) {
        KeshProperties properties = new KeshProperties();
        return new WritingEngine(
                new LanguageToolGrammarChecker(properties),
                llm,
                new ModelRouter(properties),
                new WritingPreferenceStore(properties, new ObjectMapper()),
                new WritingTurnStore(),
                properties
        );
    }

    private static ConversationContext context() {
        return new ConversationContext("c1", WorkspaceMode.PROFESSIONAL, "You are Kesh.");
    }

    private static RequestAnalysis analysis(CapabilityIntent intent) {
        return new RequestAnalysis(
                Intent.WRITING,
                0.9,
                Risk.READ,
                0.9,
                List.of(),
                true,
                Set.of(),
                false,
                null,
                ClassificationResult.single(intent, 0.9, java.util.Map.of(), "test"),
                null
        );
    }
}
