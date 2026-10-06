package com.kesh.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextTokenizer {

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9][a-z0-9_./~+-]*");

    private TextTokenizer() {
    }

    public static List<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN.matcher(lower);
        List<String> unigrams = new ArrayList<>();
        while (matcher.find()) {
            unigrams.add(matcher.group());
        }
        List<String> features = new ArrayList<>(unigrams);
        for (int i = 0; i + 1 < unigrams.size(); i++) {
            features.add(unigrams.get(i) + "_" + unigrams.get(i + 1));
        }
        return features;
    }
}
