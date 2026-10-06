package com.kesh.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;
import com.kesh.nlp.capability.CapabilityDomain;
import com.kesh.nlp.capability.CapabilityExample;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.nlp.capability.CapabilityTrainingData;
import com.kesh.nlp.capability.ClassificationResult;
import com.kesh.nlp.capability.DeskContext;
import com.kesh.nlp.capability.HybridCapabilityClassifier;
import com.kesh.nlp.capability.OpenNlpCapabilityClassifier;

/**
 * Routing regression over evaluation.jsonl. Writes a report under target/kesh-benchmark.
 */
class RoutingBenchmarkTest {

    @Test
    void routingBenchmarkWritesReportAndStaysAboveFloor() throws Exception {
        CapabilityTrainingData data = new CapabilityTrainingData(
                new ObjectMapper(),
                new ClassPathResource("nlp/kesh-intents.jsonl"),
                new ClassPathResource("nlp/evaluation.jsonl")
        );
        KeshProperties properties = new KeshProperties();
        properties.getNlp().getClassifier().setFallbackToLlm(false);
        properties.getNlp().getClassifier().setClarificationEnabled(false);
        HybridCapabilityClassifier classifier = new HybridCapabilityClassifier(
                properties,
                new OpenNlpCapabilityClassifier(data)
        );

        int correct = 0;
        int terminalFalsePositive = 0;
        int total = data.eval().size();
        Map<CapabilityDomain, int[]> byDomain = new EnumMap<>(CapabilityDomain.class);
        for (CapabilityDomain domain : CapabilityDomain.values()) {
            byDomain.put(domain, new int[]{0, 0});
        }
        StringBuilder misses = new StringBuilder();
        for (CapabilityExample example : data.eval()) {
            ClassificationResult result = classifier.classify(example.text(), DeskContext.of(null));
            boolean ok = result.intent() == example.intent()
                    || result.domain() == example.domain()
                    || result.intents().stream().anyMatch(item -> item.intent() == example.intent());
            byDomain.get(example.domain())[1]++;
            if (ok) {
                correct++;
                byDomain.get(example.domain())[0]++;
            } else {
                misses.append("- `").append(example.text().replace("`", "'")).append("` expected ")
                        .append(example.intent()).append(" got ").append(result.intent()).append('\n');
            }
            if (result.intent() == CapabilityIntent.TERMINAL_EXECUTION
                    && example.intent() != CapabilityIntent.TERMINAL_EXECUTION) {
                terminalFalsePositive++;
            }
        }
        double accuracy = correct / (double) total;
        Path dir = Path.of("target", "kesh-benchmark");
        Files.createDirectories(dir);
        Path report = dir.resolve("routing-report.md");
        Files.writeString(report, """
                # Kesh routing benchmark

                - examples: %s
                - correct: %s
                - accuracy: %.3f
                - terminal false positives: %s

                ## Domain hit rate

                %s

                ## Misses

                %s
                """.formatted(
                total,
                correct,
                accuracy,
                terminalFalsePositive,
                domainTable(byDomain),
                misses.isEmpty() ? "_none_\n" : misses
        ));
        Path published = Path.of("kesh-benchmark", "latest-routing-report.md");
        Files.createDirectories(published.getParent());
        Files.writeString(published, Files.readString(report));

        assertThat(accuracy).as("routing accuracy %s", accuracy).isGreaterThanOrEqualTo(0.70);
        assertThat(terminalFalsePositive).as("terminal false positives").isLessThanOrEqualTo(2);
        assertThat(report).exists();
    }

    private static String domainTable(Map<CapabilityDomain, int[]> byDomain) {
        StringBuilder table = new StringBuilder("| Domain | Hit | Total |\n| --- | --- | --- |\n");
        for (CapabilityDomain domain : CapabilityDomain.values()) {
            int[] counts = byDomain.get(domain);
            if (counts[1] == 0) {
                continue;
            }
            table.append("| ").append(domain).append(" | ").append(counts[0]).append(" | ")
                    .append(counts[1]).append(" |\n");
        }
        return table.toString();
    }
}
