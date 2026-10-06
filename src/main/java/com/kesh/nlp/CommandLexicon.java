package com.kesh.nlp;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects a real "run this on the machine" request.
 * Compare questions and markdown formatting are not shell asks.
 */
public final class CommandLexicon {

    private static final Pattern SHELL_ASK = Pattern.compile(
            "(?i)\\b(run|execute|shell command|in the terminal|from the terminal|open a terminal)\\b"
                    + "|\\b(curl|brew|gradle|lein)\\s+\\S+"
                    + "|\\b(mvn|npm|npx|pip|cargo|make)\\s+\\S+"
    );
    private static final Pattern IMPERATIVE = Pattern.compile(
            "(?i)^(please\\s+)?(run|execute)\\b"
    );
    private static final Pattern JAVA_SNIPPET_ASK = Pattern.compile(
            "(?i)\\b(java (code|snippet|program|class|example)|this java|using java)\\b"
    );
    private static final Pattern JAVA_EXECUTION_ASK = Pattern.compile(
            "(?i)\\b(run|execute|test)\\b.{0,48}\\b(this )?(java (code|snippet|program|implementation)|java)\\b"
                    + "|(?i)\\b(java (code|snippet|program|implementation))\\b.{0,24}\\b(run|execute|test)\\b"
    );

    private CommandLexicon() {
    }

    public static boolean isShellAsk(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (RuntimeLexicon.isVersionAsk(lower)) {
            return false;
        }
        if (JAVA_SNIPPET_ASK.matcher(lower).find()) {
            return false;
        }
        return SHELL_ASK.matcher(lower).find();
    }

    public static boolean isImperativeShellAsk(String text) {
        if (!isShellAsk(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT).strip();
        if (lower.startsWith("how ") || lower.startsWith("tell me how") || lower.contains("how do i")
                || lower.contains("how can i") || lower.contains("how to ")) {
            return false;
        }
        return IMPERATIVE.matcher(lower).find();
    }

    public static boolean isJavaExecutionAsk(String text) {
        return text != null && !text.isBlank() && JAVA_EXECUTION_ASK.matcher(text).find();
    }
}
