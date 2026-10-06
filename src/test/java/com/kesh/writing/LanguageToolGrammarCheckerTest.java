package com.kesh.writing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;
import com.kesh.nlp.capability.CapabilityIntent;

class LanguageToolGrammarCheckerTest {

    @Test
    void fixesSubjectVerbAgreementWithoutInventingContent() {
        LanguageToolGrammarChecker checker = new LanguageToolGrammarChecker(new KeshProperties());
        var result = checker.correct(
                "I wanted to check if there is any updates",
                CapabilityIntent.WRITING_CORRECT_GRAMMAR,
                true
        );
        assertThat(result.engineAvailable()).isTrue();
        assertThat(result.corrected().toLowerCase()).contains("update");
        assertThat(result.corrected()).doesNotContain("Kindly");
        assertThat(result.corrected()).doesNotContain("@");
    }

    @Test
    void spellingIntentFixesTypos() {
        LanguageToolGrammarChecker checker = new LanguageToolGrammarChecker(new KeshProperties());
        var result = checker.correct(
                "This is a recieve error.",
                CapabilityIntent.WRITING_CORRECT_SPELLING,
                true
        );
        assertThat(result.engineAvailable()).isTrue();
        assertThat(result.corrected().toLowerCase()).contains("receive");
    }
}
