package com.kesh.nlp.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kesh.config.KeshProperties;

class CapabilityClassifierEvalTest {

    private OpenNlpCapabilityClassifier classifier;
    private CapabilityTrainingData data;

    @BeforeEach
    void setUp() {
        data = new CapabilityTrainingData(
                new ObjectMapper(),
                new ClassPathResource("nlp/kesh-intents.jsonl"),
                new ClassPathResource("nlp/evaluation.jsonl")
        );
        classifier = new OpenNlpCapabilityClassifier(data);
    }

    @Test
    void evalAccuracyBeatsKeywordBaselineFloor() {
        int correct = 0;
        Map<CapabilityDomain, int[]> falseActivation = new EnumMap<>(CapabilityDomain.class);
        for (CapabilityDomain domain : CapabilityDomain.values()) {
            falseActivation.put(domain, new int[]{0, 0});
        }
        for (CapabilityExample example : data.eval()) {
            ClassificationResult result = classifier.classify(example.text(), DeskContext.of(null));
            boolean ok = result.intent() == example.intent()
                    || result.domain() == example.domain()
                    || result.intents().stream().anyMatch(item -> item.intent() == example.intent());
            if (ok) {
                correct++;
            } else {
                int[] counts = falseActivation.get(result.domain());
                counts[0]++;
            }
            falseActivation.get(example.domain())[1]++;
        }
        double accuracy = correct / (double) data.eval().size();
        assertThat(accuracy)
                .as("eval accuracy %s (%s/%s)", accuracy, correct, data.eval().size())
                .isGreaterThanOrEqualTo(0.70);
    }

    @Test
    void hardNegativesDoNotActivateTheWrongCapability() {
        KeshProperties properties = new KeshProperties();
        properties.getNlp().getClassifier().setFallbackToLlm(false);
        properties.getNlp().getClassifier().setClarificationEnabled(false);
        HybridCapabilityClassifier hybrid = new HybridCapabilityClassifier(properties, classifier);

        ClassificationResult howMvn = hybrid.classify("Tell me how to run mvn test", DeskContext.of(null));
        assertThat(howMvn.intent()).isNotEqualTo(CapabilityIntent.TERMINAL_EXECUTION);

        ClassificationResult runMvn = hybrid.classify("run mvn test", DeskContext.of(null));
        assertThat(runMvn.intent()).isEqualTo(CapabilityIntent.TERMINAL_EXECUTION);

        ClassificationResult howChrome = hybrid.classify("How do I open Chrome programmatically?", DeskContext.of(null));
        assertThat(howChrome.intent()).isNotEqualTo(CapabilityIntent.APP_OPEN);

        ClassificationResult openChrome = hybrid.classify("Open Chrome", DeskContext.of(null));
        assertThat(openChrome.intent()).isEqualTo(CapabilityIntent.APP_OPEN);

        ClassificationResult latestJava = hybrid.classify("what is the latest Java version?", DeskContext.of(null));
        assertThat(latestJava.intent()).isEqualTo(CapabilityIntent.WEB_SEARCH);

        ClassificationResult installedJava = hybrid.classify("which Java version is installed?", DeskContext.of(null));
        assertThat(installedJava.intent()).isEqualTo(CapabilityIntent.RUNTIME_INSPECT);

        ClassificationResult latestJavaShort = hybrid.classify("latest Java version", DeskContext.of(null));
        assertThat(latestJavaShort.intent()).isEqualTo(CapabilityIntent.WEB_SEARCH);
        assertThat(latestJavaShort.intent()).isNotEqualTo(CapabilityIntent.RUNTIME_INSPECT);

        ClassificationResult helloJava = hybrid.classify("Hello world Java program", DeskContext.of(null));
        assertThat(helloJava.needsClarification()).isFalse();
        assertThat(helloJava.intent()).isEqualTo(CapabilityIntent.LLM_CODE_HELP);

        ClassificationResult fib = hybrid.classify("what is the 100th Fibonacci number", DeskContext.of(null));
        assertThat(fib.intent()).isNotEqualTo(CapabilityIntent.JAVA_SNIPPET_EXECUTION);

        ClassificationResult explainAlgo = hybrid.classify("Explain this Java algorithm", DeskContext.of(null));
        assertThat(explainAlgo.intent()).isNotEqualTo(CapabilityIntent.JAVA_SNIPPET_EXECUTION);

        ClassificationResult howWeather = hybrid.classify("how is weather in Pune", DeskContext.of(null));
        assertThat(howWeather.needsClarification()).isFalse();
        assertThat(howWeather.intent()).isEqualTo(CapabilityIntent.WEB_SEARCH);

        ClassificationResult howEmail = hybrid.classify("How do I write a professional email?", DeskContext.of(null));
        assertThat(howEmail.intent()).isEqualTo(CapabilityIntent.LLM_EXPLAIN);

        ClassificationResult writeEmail = hybrid.classify("Write a professional email.", DeskContext.of(null));
        assertThat(writeEmail.intent()).isEqualTo(CapabilityIntent.WRITING_DRAFT_EMAIL);

        ClassificationResult howGrammar = hybrid.classify("How can I fix grammar in this?", DeskContext.of(null));
        assertThat(howGrammar.intent()).isEqualTo(CapabilityIntent.LLM_EXPLAIN);

        ClassificationResult fixGrammar = hybrid.classify("Fix the grammar in this.", DeskContext.of(null));
        assertThat(fixGrammar.intent()).isEqualTo(CapabilityIntent.WRITING_CORRECT_GRAMMAR);

        ClassificationResult howSlack = hybrid.classify("How should I write a Slack message?", DeskContext.of(null));
        assertThat(howSlack.intent()).isEqualTo(CapabilityIntent.LLM_EXPLAIN);

        ClassificationResult replyHow = hybrid.classify("How should I reply to this email?", DeskContext.of(null));
        assertThat(replyHow.intent()).isEqualTo(CapabilityIntent.LLM_EXPLAIN);

        ClassificationResult replyDo = hybrid.classify("Reply to this email.", DeskContext.of(null));
        assertThat(replyDo.intent()).isEqualTo(CapabilityIntent.WRITING_DRAFT_REPLY);

        ClassificationResult formatMessage = hybrid.classify("format this message", DeskContext.of(null));
        assertThat(formatMessage.intent()).isEqualTo(CapabilityIntent.WRITING_FORMAT);

        ClassificationResult tellAi = hybrid.classify("tell me about AI system design", DeskContext.of(null));
        assertThat(tellAi.intent()).isEqualTo(CapabilityIntent.LLM_EXPLAIN);

        ClassificationResult latestPatterns = hybrid.classify(
                "what are the latest AI system design patterns?", DeskContext.of(null)
        );
        assertThat(latestPatterns.intent()).isEqualTo(CapabilityIntent.WEB_SEARCH);

        ClassificationResult onePlusOne = hybrid.classify("1+1", DeskContext.of(null));
        assertThat(onePlusOne.needsClarification()).isFalse();
        assertThat(onePlusOne.intent()).isIn(CapabilityIntent.LLM_EXPLAIN, CapabilityIntent.LLM_CODE_HELP, CapabilityIntent.CHAT_GENERAL);
        assertThat(onePlusOne.intent()).isNotEqualTo(CapabilityIntent.RUNTIME_INSPECT);
    }

    @Test
    void tellMeHowToRunMvnIsCodeHelp() {
        ClassificationResult result = classifier.classify("Tell me how to run mvn test", DeskContext.of(null));
        assertThat(result.intent())
                .as("tokens=%s source=%s intents=%s",
                        java.util.Arrays.toString(OpenNlpCapabilityClassifier.tokens("Tell me how to run mvn test")),
                        result.source(),
                        result.intents())
                .isEqualTo(CapabilityIntent.LLM_CODE_HELP);
    }
}
