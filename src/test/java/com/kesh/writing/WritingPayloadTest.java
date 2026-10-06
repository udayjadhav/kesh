package com.kesh.writing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.kesh.nlp.capability.CapabilityIntent;

class WritingPayloadTest {

    @Test
    void extractsTextAfterColon() {
        WritingPayload payload = WritingPayload.extract(
                "fix grammar:\nI wanted to check if there is any updates",
                CapabilityIntent.WRITING_CORRECT_GRAMMAR,
                "",
                ""
        );
        assertThat(payload.source()).contains("I wanted to check if there is any updates");
        assertThat(payload.instruction().toLowerCase()).contains("fix grammar");
    }

    @Test
    void draftEmailDoesNotInventSource() {
        WritingPayload payload = WritingPayload.extract(
                "write a recruiter follow-up email",
                CapabilityIntent.WRITING_DRAFT_EMAIL,
                "",
                ""
        );
        assertThat(payload.source()).isBlank();
        assertThat(payload.channel()).isEqualTo(WritingChannel.EMAIL);
    }

    @Test
    void slackInstructionUsesSlackChannel() {
        WritingPayload payload = WritingPayload.extract(
                "make this Slack-friendly:\nI would like to request an update",
                CapabilityIntent.WRITING_DRAFT_SLACK,
                "",
                ""
        );
        assertThat(payload.channel()).isEqualTo(WritingChannel.SLACK);
        assertThat(payload.source()).contains("I would like to request an update");
    }

    @Test
    void trailingFormatInstructionKeepsTheMessageAsSource() {
        WritingPayload payload = WritingPayload.extract(
                "Hi Team ,\nNot feelin well , day off\n\nformat this message",
                CapabilityIntent.WRITING_FORMAT,
                "",
                ""
        );
        assertThat(payload.source()).contains("Not feelin well");
        assertThat(payload.instruction().toLowerCase()).contains("format this message");
    }
}
