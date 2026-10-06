package com.kesh.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EntityExtractorTest {

    private final EntityExtractor extractor = new EntityExtractor();

    @Test
    void extractsPathsTicketsEmailsAndChannels() {
        List<String> found = extractor.extract(
                "Read ~/Kesh/README.md and ping #eng about PROJ-2912, then mail ada@example.com"
        );
        assertThat(found).contains("~/Kesh/README.md", "PROJ-2912", "ada@example.com", "#eng");
    }
}
