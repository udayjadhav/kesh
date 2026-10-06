package com.kesh.nlp;

import java.util.Locale;
import java.util.Set;

/**
 * Short replies that are conversation moves, not new topics.
 * Not a capability router — policy still decides what may run.
 */
public final class DiscourseActs {

    private static final Set<String> CONFIRMATIONS = Set.of(
            "yes",
            "yeah",
            "yep",
            "yup",
            "sure",
            "please",
            "do it",
            "go ahead",
            "check it",
            "yes please",
            "please do",
            "please check",
            "sure thing",
            "yes check",
            "yes do it"
    );

    private DiscourseActs() {
    }

    public static boolean isConfirmation(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return CONFIRMATIONS.contains(normalize(text));
    }

    public static boolean offeredLocalInstallCheck(String assistantMessage) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return false;
        }
        String lower = assistantMessage.toLowerCase(Locale.ROOT);
        return lower.contains("installed on this mac")
                || lower.contains("installed on this machine")
                || lower.contains("check the version")
                || (lower.contains("version of") && lower.contains("installed"));
    }

    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").strip().replaceAll("\\s+", " ");
    }
}
