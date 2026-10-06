package com.kesh.api;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kesh.learning.FeedbackEvent;
import com.kesh.learning.LearningEventStore;
import com.kesh.workspace.WorkspaceMode;
import com.kesh.writing.WritingDatasetStore;
import com.kesh.writing.WritingPreferenceStore;
import com.kesh.writing.WritingTurnStore;

@RestController
@RequestMapping("/{desk:work|personal|professional}/api")
public class FeedbackController {

    private final LearningEventStore store;
    private final WritingTurnStore writingTurns;
    private final WritingDatasetStore writingDataset;
    private final WritingPreferenceStore writingPreferences;

    public FeedbackController(
            LearningEventStore store,
            WritingTurnStore writingTurns,
            WritingDatasetStore writingDataset,
            WritingPreferenceStore writingPreferences
    ) {
        this.store = store;
        this.writingTurns = writingTurns;
        this.writingDataset = writingDataset;
        this.writingPreferences = writingPreferences;
    }

    @PostMapping("/feedback")
    public Map<String, Object> feedback(@PathVariable String desk, @RequestBody Map<String, Object> body) {
        WorkspaceMode mode = WorkspaceMode.fromPath(desk);
        String type = string(body, "type", "NEGATIVE");
        String reason = string(body, "reason", "");
        String comment = string(body, "comment", "");
        String interactionId = string(body, "interactionId", "");
        store.appendFeedback(new FeedbackEvent(
                interactionId,
                string(body, "conversationId", ""),
                mode.path(),
                type,
                reason,
                comment
        ));
        WritingTurnStore.WritingTurn turn = writingTurns.get(interactionId);
        if (turn != null) {
            writingPreferences.learnFromFeedback(mode, reason, comment);
            boolean accepted = "POSITIVE".equalsIgnoreCase(type);
            writingDataset.append(mode, turn, accepted, firstNonBlank(comment, reason));
        }
        return Map.of("ok", true);
    }

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right == null ? "" : right;
    }

    private static String string(Map<String, Object> body, String key, String fallback) {
        if (body == null || body.get(key) == null) {
            return fallback;
        }
        String value = body.get(key).toString().strip();
        return value.isBlank() ? fallback : value;
    }
}
