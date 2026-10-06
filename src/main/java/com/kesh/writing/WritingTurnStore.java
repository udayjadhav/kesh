package com.kesh.writing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class WritingTurnStore {

    private static final int CAP = 400;

    private final ConcurrentHashMap<String, WritingTurn> turns = new ConcurrentHashMap<>();

    public void put(WritingTurn turn) {
        if (turn == null || turn.interactionId() == null || turn.interactionId().isBlank()) {
            return;
        }
        if (turns.size() >= CAP) {
            turns.keySet().stream().findFirst().ifPresent(turns::remove);
        }
        turns.put(turn.interactionId(), turn);
    }

    public WritingTurn get(String interactionId) {
        if (interactionId == null || interactionId.isBlank()) {
            return null;
        }
        return turns.get(interactionId);
    }

    public record WritingTurn(
            String interactionId,
            String conversationId,
            String desk,
            String task,
            String context,
            String input,
            String output,
            Map<String, Double> scores
    ) {
    }
}
