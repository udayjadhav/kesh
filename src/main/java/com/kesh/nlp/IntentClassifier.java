package com.kesh.nlp;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class IntentClassifier {

    private static final Pattern WORK_HINT = Pattern.compile(
            "\\b(slack|jira|confluence|gmail|github|pull request|\\bpr\\b|ticket)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DESTRUCTIVE_HINT = Pattern.compile(
            "\\b(delete|remove|rm |trash|wipe|format|overwrite)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FILES_HINT = Pattern.compile(
            "\\b(file|folder|directory|read|open|write|save|list|~/|\\.md|\\.txt|\\.json)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern APPS_HINT = Pattern.compile(
            "\\b(calendar|notes|safari|chrome|browser|open app|launch)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final MultinomialNaiveBayes model = new MultinomialNaiveBayes();

    public IntentClassifier(NlpTrainingData trainingData) {
        model.train(trainingData.intentExamples());
    }

    public MultinomialNaiveBayes.Prediction raw(String text) {
        return model.predict(text);
    }

    public Intent classify(String text) {
        if (RuntimeLexicon.isVersionAsk(text)) {
            return Intent.INSPECT;
        }
        if (CommandLexicon.isShellAsk(text)) {
            return Intent.TERMINAL;
        }
        if (WebLexicon.isFetchAsk(text)) {
            return Intent.WEB_FETCH;
        }
        if (WebLexicon.isResearchAsk(text)) {
            return Intent.WEB_RESEARCH;
        }
        if (WebLexicon.isSearchAsk(text)) {
            return Intent.WEB_SEARCH;
        }
        MultinomialNaiveBayes.Prediction prediction = model.predict(text);
        Intent fromModel = Intent.valueOf(prediction.label());
        if (prediction.confidence() >= 0.55) {
            if (fromModel == Intent.TERMINAL && !CommandLexicon.isShellAsk(text)) {
                return fallback(text, Intent.CHAT);
            }
            if (isWebIntent(fromModel) && !WebLexicon.isAnyWebAsk(text)) {
                return fallback(text, Intent.CHAT);
            }
            return fromModel;
        }
        return fallback(text, fromModel);
    }

    private Intent fallback(String text, Intent fromModel) {
        if (text == null) {
            return Intent.CHAT;
        }
        if (DESTRUCTIVE_HINT.matcher(text).find()) {
            return Intent.DESTRUCTIVE;
        }
        if (RuntimeLexicon.isVersionAsk(text)) {
            return Intent.INSPECT;
        }
        if (WORK_HINT.matcher(text).find()) {
            return Intent.WORK;
        }
        if (CommandLexicon.isShellAsk(text)) {
            return Intent.TERMINAL;
        }
        if (WebLexicon.isFetchAsk(text)) {
            return Intent.WEB_FETCH;
        }
        if (WebLexicon.isResearchAsk(text)) {
            return Intent.WEB_RESEARCH;
        }
        if (WebLexicon.isSearchAsk(text)) {
            return Intent.WEB_SEARCH;
        }
        if (APPS_HINT.matcher(text).find()) {
            return Intent.APPS;
        }
        if (FILES_HINT.matcher(text).find()) {
            return Intent.FILES;
        }
        return fromModel != null ? fromModel : Intent.CHAT;
    }

    public double confidence(String text) {
        return model.predict(text).confidence();
    }

    static boolean looksLikeWork(String text) {
        return text != null && WORK_HINT.matcher(text).find();
    }

    private static boolean isWebIntent(Intent intent) {
        return intent == Intent.WEB_SEARCH || intent == Intent.WEB_FETCH || intent == Intent.WEB_RESEARCH;
    }
}
