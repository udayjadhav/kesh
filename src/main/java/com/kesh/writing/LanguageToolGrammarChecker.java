package com.kesh.writing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.languagetool.JLanguageTool;
import org.languagetool.Languages;
import org.languagetool.rules.RuleMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;
import com.kesh.nlp.capability.CapabilityIntent;

/**
 * Local LanguageTool. Never calls a public grammar API.
 */
@Component
public class LanguageToolGrammarChecker {

    private static final Logger log = LoggerFactory.getLogger(LanguageToolGrammarChecker.class);

    private static final Set<String> SPELLING = Set.of("TYPOS", "CONFUSED_WORDS");
    private static final Set<String> PUNCTUATION = Set.of("PUNCTUATION", "TYPOGRAPHY");
    private static final Set<String> STYLE = Set.of("STYLE", "REDUNDANCY", "PLAIN_ENGLISH", "COLLOQUIALISMS");

    private final KeshProperties properties;
    private volatile JLanguageTool languageTool;
    private volatile boolean unavailable;

    public LanguageToolGrammarChecker(KeshProperties properties) {
        this.properties = properties;
    }

    public CorrectionResult correct(String text, CapabilityIntent intent, boolean keepWording) {
        String original = text == null ? "" : text;
        if (original.isBlank()) {
            return new CorrectionResult(original, original, List.of(), true);
        }
        JLanguageTool tool = tool();
        if (tool == null) {
            return new CorrectionResult(original, original, List.of(), false);
        }
        try {
            List<RuleMatch> matches;
            synchronized (tool) {
                matches = tool.check(original);
            }
            List<Map<String, Object>> applied = new ArrayList<>();
            StringBuilder edited = new StringBuilder(original);
            List<RuleMatch> ordered = new ArrayList<>(matches);
            ordered.sort((a, b) -> Integer.compare(b.getFromPos(), a.getFromPos()));
            for (RuleMatch match : ordered) {
                if (!accept(match, intent, keepWording)) {
                    continue;
                }
                List<String> suggestions = match.getSuggestedReplacements();
                if (suggestions == null || suggestions.isEmpty()) {
                    continue;
                }
                String replacement = suggestions.getFirst();
                int from = match.getFromPos();
                int to = match.getToPos();
                if (from < 0 || to > edited.length() || from > to) {
                    continue;
                }
                edited.replace(from, to, replacement);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rule", match.getRule().getId());
                row.put("category", category(match));
                row.put("from", from);
                row.put("to", to);
                row.put("replacement", replacement);
                applied.add(0, row);
            }
            return new CorrectionResult(original, edited.toString(), List.copyOf(applied), true);
        } catch (IOException e) {
            log.info("LanguageTool check skipped: {}", e.getMessage());
            return new CorrectionResult(original, original, List.of(), false);
        }
    }

    private boolean accept(RuleMatch match, CapabilityIntent intent, boolean keepWording) {
        String category = category(match);
        if (keepWording && STYLE.contains(category)) {
            return false;
        }
        if (intent == CapabilityIntent.WRITING_CORRECT_SPELLING) {
            return SPELLING.contains(category);
        }
        if (intent == CapabilityIntent.WRITING_FIX_PUNCTUATION) {
            return PUNCTUATION.contains(category);
        }
        if (intent == CapabilityIntent.WRITING_CORRECT_GRAMMAR || keepWording) {
            return !STYLE.contains(category);
        }
        return true;
    }

    private static String category(RuleMatch match) {
        if (match.getRule() == null || match.getRule().getCategory() == null
                || match.getRule().getCategory().getId() == null) {
            return "";
        }
        return match.getRule().getCategory().getId().toString().toUpperCase(Locale.ROOT);
    }

    private JLanguageTool tool() {
        if (unavailable) {
            return null;
        }
        if (languageTool != null) {
            return languageTool;
        }
        synchronized (this) {
            if (languageTool != null || unavailable) {
                return languageTool;
            }
            try {
                String code = properties.getWriting().getLanguage();
                if (code == null || code.isBlank()) {
                    code = "en-US";
                }
                languageTool = new JLanguageTool(Languages.getLanguageForShortCode(code));
                log.info("LanguageTool ready language={}", code);
                return languageTool;
            } catch (RuntimeException e) {
                unavailable = true;
                log.warn("LanguageTool unavailable: {}", e.getMessage());
                return null;
            }
        }
    }

    public record CorrectionResult(
            String original,
            String corrected,
            List<Map<String, Object>> matches,
            boolean engineAvailable
    ) {
        public boolean changed() {
            return original != null && corrected != null && !original.equals(corrected);
        }
    }
}
