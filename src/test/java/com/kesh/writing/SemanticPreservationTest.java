package com.kesh.writing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SemanticPreservationTest {

    @Test
    void keepsUrlsNumbersAndNames() {
        String original = "Call Priya at https://example.com by Friday 12 about PROJ-1042.";
        String good = "Please call Priya at https://example.com by Friday 12 about PROJ-1042.";
        String bad = "Please call someone later about the ticket.";
        assertThat(SemanticPreservation.missingFacts(original, good)).isEmpty();
        assertThat(SemanticPreservation.missingFacts(original, bad)).contains("12");
        assertThat(SemanticPreservation.preserved(original, good, 0.3)).isTrue();
        assertThat(SemanticPreservation.preserved(original, bad, 0.9)).isFalse();
    }

    @Test
    void jaccardIsHigherWhenMeaningStays() {
        double close = SemanticPreservation.jaccard(
                "I wanted to check if there are any updates",
                "I wanted to check if there are any updates."
        );
        double far = SemanticPreservation.jaccard(
                "I wanted to check if there are any updates",
                "The deployment is complete and we should ship tomorrow."
        );
        assertThat(close).isGreaterThan(far);
    }
}
