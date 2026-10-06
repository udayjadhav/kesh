package com.kesh.nlp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class EntityExtractor {

    private static final Pattern PATH = Pattern.compile("(?:~|/Users)[/\\w.\\-]+|\\./[\\w./\\-]+|[\\w.\\-]+\\.(?:md|txt|json|yml|yaml|csv|log)");
    private static final Pattern TICKET = Pattern.compile("\\b[A-Z]{2,10}-\\d+\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern CHANNEL = Pattern.compile("#[\\w.-]+");
    private static final Pattern RUNTIME = Pattern.compile(
            "(?i)\\b(java|jdk|openjdk|node|npm|python|cursor|macos|mac|git|docker|mvn|maven)\\b"
    );

    public List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        addAll(found, PATH, text);
        addAll(found, TICKET, text);
        addAll(found, EMAIL, text);
        addAll(found, URL, text);
        addAll(found, CHANNEL, text);
        addAll(found, RUNTIME, text);
        String runtime = RuntimeLexicon.findCanonical(text);
        if (runtime != null) {
            found.add(runtime);
        }
        return List.copyOf(found);
    }

    private static void addAll(Set<String> found, Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
    }
}
