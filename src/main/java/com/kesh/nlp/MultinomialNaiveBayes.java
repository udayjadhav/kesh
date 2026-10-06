package com.kesh.nlp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultinomialNaiveBayes {

    private final Map<String, Map<String, Integer>> tokenCounts = new HashMap<>();
    private final Map<String, Integer> classTokenTotals = new HashMap<>();
    private final Map<String, Integer> classDocs = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private int documentCount;

    public void train(List<LabeledExample> examples) {
        for (LabeledExample example : examples) {
            String label = example.label();
            classDocs.merge(label, 1, Integer::sum);
            documentCount++;
            Map<String, Integer> counts = tokenCounts.computeIfAbsent(label, key -> new HashMap<>());
            for (String token : TextTokenizer.tokens(example.text())) {
                vocabulary.add(token);
                counts.merge(token, 1, Integer::sum);
                classTokenTotals.merge(label, 1, Integer::sum);
            }
        }
    }

    public Prediction predict(String text) {
        if (documentCount == 0) {
            throw new IllegalStateException("Classifier has no training data");
        }
        List<String> tokens = TextTokenizer.tokens(text);
        String bestLabel = null;
        double bestLog = Double.NEGATIVE_INFINITY;
        Map<String, Double> logScores = new HashMap<>();
        int vocabSize = Math.max(1, vocabulary.size());
        for (String label : classDocs.keySet()) {
            double logProb = Math.log(classDocs.get(label) / (double) documentCount);
            int total = classTokenTotals.getOrDefault(label, 0);
            Map<String, Integer> counts = tokenCounts.getOrDefault(label, Map.of());
            for (String token : tokens) {
                int count = counts.getOrDefault(token, 0);
                logProb += Math.log((count + 1.0) / (total + vocabSize));
            }
            logScores.put(label, logProb);
            if (logProb > bestLog) {
                bestLog = logProb;
                bestLabel = label;
            }
        }
        double confidence = softmaxConfidence(logScores, bestLabel);
        return new Prediction(bestLabel, confidence, Map.copyOf(logScores));
    }

    private static double softmaxConfidence(Map<String, Double> logScores, String bestLabel) {
        double max = logScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double sum = 0;
        for (double log : logScores.values()) {
            sum += Math.exp(log - max);
        }
        double best = Math.exp(logScores.get(bestLabel) - max);
        return sum == 0 ? 0 : best / sum;
    }

    public record Prediction(String label, double confidence, Map<String, Double> logScores) {
    }
}
