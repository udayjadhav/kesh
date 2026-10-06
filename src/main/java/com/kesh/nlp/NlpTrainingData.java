package com.kesh.nlp;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class NlpTrainingData {

    private final List<LabeledExample> intentExamples;
    private final List<LabeledExample> riskExamples;

    public NlpTrainingData(
            @Value("classpath:nlp/intent-train.tsv") Resource intentTrain,
            @Value("classpath:nlp/risk-train.tsv") Resource riskTrain
    ) {
        this.intentExamples = TrainingSetLoader.load(intentTrain);
        this.riskExamples = TrainingSetLoader.load(riskTrain);
    }

    public List<LabeledExample> intentExamples() {
        return intentExamples;
    }

    public List<LabeledExample> riskExamples() {
        return riskExamples;
    }
}
