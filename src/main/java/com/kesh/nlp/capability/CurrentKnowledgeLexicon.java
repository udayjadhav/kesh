package com.kesh.nlp.capability;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.kesh.nlp.WebLexicon;

/**
 * Request <em>shapes</em> that need live public facts. Not a company-name router.
 */
public final class CurrentKnowledgeLexicon {

    private static final Pattern MARKET_QUOTE = Pattern.compile(
            "(?i)\\b(stock|share|shares|ticker|equity|nasdaq|nyse)\\b.{0,40}\\b(price|quote|trading|doing|close|open|today)\\b"
                    + "|(?i)\\b(price|quote)\\b.{0,40}\\b(stock|share|shares|ticker)\\b"
                    + "|(?i)\\bhow(?:['’]?s| is)\\b.{0,48}\\b(stock|share|shares)\\b"
    );
    private static final Pattern PUBLIC_EVENT = Pattern.compile(
            "(?i)\\b(layoff|layoffs|earnings|ipo|merger|acquisition|restructuring|lawsuit|recall"
                    + "|outage|strike|bankruptcy|dividend|guidance|announcement|announced)\\b"
    );
    private static final Pattern CURRENT_EVENT_ASK = Pattern.compile(
            "(?i)\\b(what happened|recent developments|latest news|breaking news|announced recently)\\b"
                    + "|(?i)\\b(news|developments)\\b.{0,24}\\b(recent|latest|today)\\b"
    );
    private static final Pattern SOURCE_ASK = Pattern.compile(
            "(?i)^(source|sources|citation|citations|link|links|url|urls)\\??$"
                    + "|(?i)\\b(source|sources|citation|where did you get|according to (whom|what|which))\\b"
                    + "|(?i)\\bcan you (share|give|show) (me )?(a |the )?source"
    );
    private static final Pattern QUANTITY_ASK = Pattern.compile(
            "(?i)\\b(how many|how much|exact number|headcount|what(?:['’]?s| is) the number)\\b"
    );
    private static final Pattern LIVE_ATTRIBUTE = Pattern.compile(
            "(?i)\\b(rain|humidity|temperature|forecast|wind|umbrella|chances|precipitation|aqi|traffic)\\b"
    );
    private static final Pattern LETTER = Pattern.compile("[a-zA-Z0-9]");
    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "in", "of", "for", "to", "from", "about", "tell", "me", "how",
            "is", "are", "what", "whats", "vs", "versus", "please", "can", "you", "check",
            "now", "today", "this", "that", "it", "and", "or", "on", "at", "with"
    );

    private CurrentKnowledgeLexicon() {
    }

    public static boolean isMarketQuoteAsk(String text) {
        return text != null && MARKET_QUOTE.matcher(text).find();
    }

    public static boolean isCurrentEventAsk(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return PUBLIC_EVENT.matcher(text).find() || CURRENT_EVENT_ASK.matcher(text).find();
    }

    public static boolean needsLivePublicFacts(String text) {
        return isMarketQuoteAsk(text)
                || isCurrentEventAsk(text)
                || isLiveAttributeAsk(text)
                || WebLexicon.isSearchAsk(text)
                || WebLexicon.isFetchAsk(text)
                || WebLexicon.isResearchAsk(text)
                || WebLexicon.isLiveConditionsAsk(text);
    }

    public static boolean isSourceVerificationAsk(String text) {
        return text != null && SOURCE_ASK.matcher(text).find();
    }

    public static boolean isQuantityAsk(String text) {
        return text != null && QUANTITY_ASK.matcher(text).find();
    }

    public static boolean isLiveAttributeAsk(String text) {
        return text != null && LIVE_ATTRIBUTE.matcher(text).find();
    }

    public static boolean hasSemanticContent(String text) {
        return text != null && LETTER.matcher(text).find();
    }

    public static boolean looksLikeIndependentLookup(String current, String previous) {
        if (!hasSemanticContent(current)) {
            return false;
        }
        Set<String> currentTokens = contentTokens(current);
        if (currentTokens.isEmpty()) {
            return false;
        }
        if (isLiveAttributeAsk(current) || isQuantityAsk(current) || isSourceVerificationAsk(current)) {
            return false;
        }
        Set<String> previousTokens = contentTokens(previous);
        for (String token : currentTokens) {
            if (!previousTokens.contains(token) && !isLiveAttributeToken(token)) {
                return true;
            }
        }
        return false;
    }

    static Set<String> contentTokens(String text) {
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        for (String raw : OpenNlpCapabilityClassifier.tokens(text)) {
            String token = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (token.length() < 2 || STOP.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static boolean isLiveAttributeToken(String token) {
        return token != null && LIVE_ATTRIBUTE.matcher(token).find();
    }
}
