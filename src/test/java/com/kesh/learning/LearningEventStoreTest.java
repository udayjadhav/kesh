package com.kesh.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;

class LearningEventStoreTest {

    @TempDir
    Path temp;

    @Test
    void negativeFeedbackCreatesPendingCandidateAndApprovalPromotesIt() {
        KeshProperties properties = new KeshProperties();
        properties.getWorkspace().setDataDir(temp.toString());
        LearningEventStore store = new LearningEventStore(properties, new ObjectMapper());
        String interactionId = UUID.randomUUID().toString();
        store.appendInteraction(new InteractionEvent(
                interactionId,
                "c1",
                "work",
                "How do I run mvn test?",
                "opennlp",
                "TERMINAL_EXECUTION",
                0.4,
                List.of(),
                List.of(),
                "gemma",
                12,
                "I will run that.",
                "2026-09-17T00:00:00Z",
                "STATIC_KNOWLEDGE",
                false,
                "",
                List.of(),
                "",
                ""
        ));
        store.appendFeedback(new FeedbackEvent(
                interactionId,
                "c1",
                "work",
                "NEGATIVE",
                "INCORRECT_INTENT",
                "I wanted instructions, not execution."
        ));
        List<LearningCandidate> pending = store.pendingCandidates("work", 10);
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().predictedIntent()).isEqualTo("TERMINAL_EXECUTION");
        assertThat(store.approve("work", pending.getFirst().id(), "LLM_CODE_HELP")).isTrue();
        assertThat(store.pendingCandidates("work", 10)).isEmpty();
        assertThat(store.approvedIntents("work", 10).getFirst().correctIntent()).isEqualTo("LLM_CODE_HELP");
    }

    @Test
    void rejectRemovesCandidateWithoutApproving() {
        KeshProperties properties = new KeshProperties();
        properties.getWorkspace().setDataDir(temp.toString());
        LearningEventStore store = new LearningEventStore(properties, new ObjectMapper());
        store.appendFeedback(new FeedbackEvent(
                "missing",
                "c1",
                "personal",
                "NEGATIVE",
                "WRONG",
                "not this"
        ));
        LearningCandidate candidate = store.pendingCandidates("personal", 10).getFirst();
        assertThat(store.reject("personal", candidate.id())).isTrue();
        assertThat(store.pendingCandidates("personal", 10)).isEmpty();
        assertThat(store.approvedIntents("personal", 10)).isEmpty();
    }
}
