package com.kesh.writing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Word-level diff for the writing UI. Does not mutate user text.
 */
public final class WritingDiff {

    private WritingDiff() {
    }

    public static List<Map<String, String>> tokens(String original, String revised) {
        List<String> left = words(original);
        List<String> right = words(revised);
        int[][] table = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) {
            for (int j = right.size() - 1; j >= 0; j--) {
                if (left.get(i).equals(right.get(j))) {
                    table[i][j] = table[i + 1][j + 1] + 1;
                } else {
                    table[i][j] = Math.max(table[i + 1][j], table[i][j + 1]);
                }
            }
        }
        List<Map<String, String>> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            if (left.get(i).equals(right.get(j))) {
                out.add(token("equal", left.get(i)));
                i++;
                j++;
            } else if (table[i + 1][j] >= table[i][j + 1]) {
                out.add(token("del", left.get(i)));
                i++;
            } else {
                out.add(token("ins", right.get(j)));
                j++;
            }
        }
        while (i < left.size()) {
            out.add(token("del", left.get(i++)));
        }
        while (j < right.size()) {
            out.add(token("ins", right.get(j++)));
        }
        return List.copyOf(out);
    }

    private static Map<String, String> token(String type, String text) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("text", text);
        return map;
    }

    private static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] raw = text.strip().split("\\s+");
        List<String> words = new ArrayList<>();
        for (String word : raw) {
            if (!word.isBlank()) {
                words.add(word);
            }
        }
        return words;
    }
}
