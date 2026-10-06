package com.kesh.nlp.capability;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.doccat.NGramFeatureGenerator;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

/**
 * Local OpenNLP MaxEnt document categorizer trained on Kesh examples.
 * Replaceable via {@code kesh.nlp.classifier.type}.
 */
@Component
public class OpenNlpCapabilityClassifier implements CapabilityClassifier {

    private static final Logger log = LoggerFactory.getLogger(OpenNlpCapabilityClassifier.class);
    private static final double MULTI_GAP = 0.32;
    private static final double SCORE_FLOOR = 0.10;
    private static final double NEIGHBOR_JACCARD = 0.38;

    private final DocumentCategorizerME categorizer;
    private final List<CapabilityExample> train;

    public OpenNlpCapabilityClassifier(CapabilityTrainingData trainingData) {
        this.train = trainingData.train();
        this.categorizer = train(trainingData.train());
        log.info("OpenNLP capability classifier trained on {} examples", trainingData.train().size());
    }

    @Override
    public String name() {
        return "opennlp";
    }

    @Override
    public ClassificationResult classify(String text, DeskContext context) {
        if (text == null || text.isBlank() || categorizer == null) {
            return ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.2, Map.of(), name());
        }
        String[] tokens = tokens(text);
        double[] scores = categorizer.categorize(tokens);
        List<Scored> ranked = ranked(scores);
        if (ranked.isEmpty()) {
            return ClassificationResult.single(CapabilityIntent.CHAT_GENERAL, 0.2, Map.of(), name());
        }
        Map<String, Object> entities = entitiesFor(text, ranked.getFirst().intent());
        List<ClassifiedIntent> intents = new ArrayList<>();
        Scored best = ranked.getFirst();
        intents.add(new ClassifiedIntent(best.intent().domain(), best.intent(), best.score(), entities));
        for (int i = 1; i < ranked.size(); i++) {
            Scored next = ranked.get(i);
            if (next.score() < SCORE_FLOOR && best.score() - next.score() > MULTI_GAP) {
                break;
            }
            if (best.score() - next.score() > MULTI_GAP && next.score() < SCORE_FLOOR) {
                break;
            }
            if (next.score() < SCORE_FLOOR) {
                continue;
            }
            if (next.intent().domain() != best.intent().domain() || next.intent() != best.intent()) {
                intents.add(new ClassifiedIntent(next.intent().domain(), next.intent(), next.score(), entitiesFor(text, next.intent())));
            }
            if (intents.size() >= 4) {
                break;
            }
        }
        ClassificationResult maxent = new ClassificationResult(intents, name(), false, null);
        ClassificationResult neighbor = nearestNeighbor(text);
        if (neighbor == null) {
            return maxent;
        }
        return ClassificationResult.prefer(neighbor, maxent).withSource("opennlp+knn");
    }

    private ClassificationResult nearestNeighbor(String text) {
        if (text == null || train == null || train.isEmpty()) {
            return null;
        }
        java.util.Set<String> needle = tokenSet(text);
        if (needle.isEmpty()) {
            return null;
        }
        CapabilityExample best = null;
        double bestScore = 0;
        for (CapabilityExample example : train) {
            java.util.Set<String> other = tokenSet(example.text());
            double score = jaccard(needle, other) * lengthFit(needle.size(), other.size());
            if (score > bestScore) {
                bestScore = score;
                best = example;
            }
        }
        if (best == null || bestScore < NEIGHBOR_JACCARD) {
            return null;
        }
        Map<String, Object> entities = entitiesFor(text, best.intent());
        List<ClassifiedIntent> intents = new ArrayList<>();
        intents.add(new ClassifiedIntent(best.intent().domain(), best.intent(), Math.min(0.93, 0.72 + bestScore / 2), entities));
        for (CapabilityIntent extra : best.extraIntents()) {
            intents.add(new ClassifiedIntent(extra.domain(), extra, 0.72, entitiesFor(text, extra)));
        }
        return new ClassificationResult(intents, "knn", false, null);
    }

    private static java.util.Set<String> tokenSet(String text) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (String token : tokens(text)) {
            if (token != null && token.length() > 1) {
                set.add(token);
            }
        }
        return set;
    }

    private static double jaccard(java.util.Set<String> a, java.util.Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int overlap = 0;
        for (String token : a) {
            if (b.contains(token)) {
                overlap++;
            }
        }
        int union = a.size() + b.size() - overlap;
        return union == 0 ? 0 : overlap / (double) union;
    }

    private static double lengthFit(int a, int b) {
        int max = Math.max(a, b);
        int min = Math.min(a, b);
        if (max == 0) {
            return 0;
        }
        return min / (double) max;
    }

    private List<Scored> ranked(double[] scores) {
        List<Scored> ranked = new ArrayList<>();
        int count = categorizer.getNumberOfCategories();
        for (int i = 0; i < count; i++) {
            CapabilityIntent intent = CapabilityIntent.parse(categorizer.getCategory(i));
            ranked.add(new Scored(intent, scores[i]));
        }
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));
        return ranked;
    }

    public static Map<String, Object> entitiesFor(String text, CapabilityIntent intent) {
        Map<String, Object> entities = new LinkedHashMap<>();
        entities.put("query", text == null ? "" : text.strip());
        String runtime = com.kesh.nlp.RuntimeLexicon.findCanonical(text);
        if (runtime != null) {
            entities.put("runtime", runtime);
        }
        String url = com.kesh.nlp.WebLexicon.firstUrl(text);
        if (url != null) {
            entities.put("url", url);
        }
        if (intent != null && intent.domain() == CapabilityDomain.SYSTEM && runtime != null) {
            entities.put("target", runtime);
        }
        if (intent != null && intent.domain() == CapabilityDomain.WRITING) {
            String payload = com.kesh.writing.WritingPayload.payloadFrom(text);
            if (!payload.isBlank()) {
                entities.put("source", payload);
            }
        }
        return entities;
    }

    private static DocumentCategorizerME train(List<CapabilityExample> examples) {
        List<DocumentSample> samples = new ArrayList<>();
        for (CapabilityExample example : examples) {
            samples.add(new DocumentSample(example.intent().name(), tokens(example.text())));
        }
        try {
            TrainingParameters params = TrainingParameters.defaultParams();
            params.put(TrainingParameters.ITERATIONS_PARAM, 120);
            params.put(TrainingParameters.CUTOFF_PARAM, 0);
            DoccatFactory factory = new DoccatFactory(new FeatureGenerator[]{
                    new BagOfWordsFeatureGenerator(),
                    new NGramFeatureGenerator(2, 3)
            });
            ObjectStream<DocumentSample> stream = ObjectStreamUtils.createObjectStream(samples);
            DoccatModel model = DocumentCategorizerME.train("en", stream, params, factory);
            return new DocumentCategorizerME(model);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to train OpenNLP capability classifier", e);
        }
    }

    public static String[] tokens(String text) {
        if (text == null || text.isBlank()) {
            return new String[]{"empty"};
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("([0-9])([+\\-*/%=^])", "$1 $2");
        normalized = normalized.replaceAll("([+\\-*/%=^])([0-9])", "$1 $2");
        normalized = normalized.replaceAll("[^a-z0-9./:_~+\\-*%=^\\s]", " ");
        String[] raw = SimpleTokenizer.INSTANCE.tokenize(normalized);
        if (raw.length == 0) {
            return new String[]{normalized.strip()};
        }
        return raw;
    }

    private record Scored(CapabilityIntent intent, double score) {
    }
}
