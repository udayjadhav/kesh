package com.kesh.nlp;

import org.springframework.stereotype.Component;

@Component
public class RiskClassifier {

    private final MultinomialNaiveBayes model = new MultinomialNaiveBayes();

    public RiskClassifier(NlpTrainingData trainingData) {
        model.train(trainingData.riskExamples());
    }

    public MultinomialNaiveBayes.Prediction raw(String text) {
        return model.predict(text);
    }

    public Risk classify(String text, Intent intent) {
        MultinomialNaiveBayes.Prediction prediction = model.predict(text);
        Risk fromModel = Risk.valueOf(prediction.label());
        if (intent == Intent.DESTRUCTIVE) {
            return Risk.DANGEROUS;
        }
        if (intent == Intent.INSPECT || intent == Intent.CHAT || intent == Intent.WRITING
                || intent == Intent.WEB_SEARCH || intent == Intent.WEB_FETCH || intent == Intent.WEB_RESEARCH) {
            return Risk.READ;
        }
        if (intent == Intent.TERMINAL && fromModel != Risk.READ) {
            return fromModel == Risk.DANGEROUS ? Risk.DANGEROUS : Risk.WRITE;
        }
        return fromModel;
    }

    public double confidence(String text) {
        return model.predict(text).confidence();
    }
}
