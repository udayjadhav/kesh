package com.kesh.nlp;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects public-web research vs local chat.
 * "What is Kafka?" stays chat. "Latest Spring Boot release" is a web search.
 */
public final class WebLexicon {

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\]\\)]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_WEB = Pattern.compile(
            "(?i)\\b(search the web|search online|web search|find online|look\\s*up online|on the internet|according to the latest documentation)\\b"
    );
    private static final Pattern RESEARCH = Pattern.compile(
            "(?i)\\b(research|look\\s*up|look up|find online|search the web)\\b"
    );
    private static final Pattern CURRENT = Pattern.compile(
            "(?i)\\b(latest|recent|up[- ]to[- ]date|what changed|changelog|release notes)\\b"
                    + "|\\bcurrent\\b.{0,32}\\b(release|version|state|status|docs?|documentation)\\b"
                    + "|\\b(release|version)\\b.{0,16}\\bcurrent\\b"
                    + "|\\bas of today\\b"
                    + "|\\breleased today\\b"
                    + "|\\bannounced today\\b"
    );
    private static final Pattern LIVE_CONDITIONS = Pattern.compile(
            "(?i)\\b(weather|forecast)\\b.{0,48}\\b(in|for|at|today|now|tonight|this week|currently)\\b"
                    + "|(?i)\\b(check|what(?:['’]?s| is)|how(?:['’]?s| is)|whats|hats)\\b.{0,32}\\b(weather|forecast|temperature)\\b"
                    + "|(?i)\\b(temperature|humidity|air quality|aqi)\\b.{0,24}\\b(in|for|at|today|now)\\b"
                    + "|(?i)\\b(traffic|congestion)\\b.{0,80}\\b(in|between|from|to|now|currently|today|near)\\b"
                    + "|(?i)\\b(check|how(?:['’]?s| is)|what(?:['’]?s| is))\\b.{0,40}\\btraffic\\b"
    );
    private static final Pattern FETCH_VERB = Pattern.compile(
            "(?i)\\b(read|fetch|open|summarize|summarise|explain|inspect)\\b"
    );

    private WebLexicon() {
    }

    public static boolean hasHttpUrl(String text) {
        return firstUrl(text) != null;
    }

    public static String firstUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = URL.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String url = matcher.group();
        while (url.endsWith(".") || url.endsWith(",") || url.endsWith(";")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static boolean hasExplicitWebCue(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return hasHttpUrl(text) || EXPLICIT_WEB.matcher(text).find();
    }

    public static boolean isFetchAsk(String text) {
        if (text == null || text.isBlank() || CommandLexicon.isShellAsk(text)) {
            return false;
        }
        String url = firstUrl(text);
        if (url == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (FETCH_VERB.matcher(lower).find() || lower.contains("url") || lower.strip().startsWith("http")) {
            return true;
        }
        return true;
    }

    public static boolean isResearchAsk(String text) {
        if (text == null || text.isBlank() || CommandLexicon.isShellAsk(text) || RequestAnalyzer.isClockQuestion(text)) {
            return false;
        }
        if (isWorkWithoutWebCue(text)) {
            return false;
        }
        return RESEARCH.matcher(text).find() && !isFetchAsk(text);
    }

    public static boolean isSearchAsk(String text) {
        if (text == null || text.isBlank() || CommandLexicon.isShellAsk(text) || RequestAnalyzer.isClockQuestion(text)) {
            return false;
        }
        if (RuntimeLexicon.isVersionAsk(text)) {
            return false;
        }
        if (isWorkWithoutWebCue(text)) {
            return false;
        }
        if (isFetchAsk(text) || isResearchAsk(text)) {
            return false;
        }
        return CURRENT.matcher(text).find() || EXPLICIT_WEB.matcher(text).find() || isLiveConditionsAsk(text);
    }

    public static boolean isLiveConditionsAsk(String text) {
        return text != null && !text.isBlank() && LIVE_CONDITIONS.matcher(text).find();
    }

    public static boolean isAnyWebAsk(String text) {
        return isFetchAsk(text) || isResearchAsk(text) || isSearchAsk(text);
    }

    private static boolean isWorkWithoutWebCue(String text) {
        return IntentClassifier.looksLikeWork(text) && !hasExplicitWebCue(text);
    }
}
