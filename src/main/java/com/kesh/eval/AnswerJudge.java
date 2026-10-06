package com.kesh.eval;

import java.util.List;

import com.kesh.nlp.RequestAnalysis;
import com.kesh.nlp.capability.CapabilityIntent;
import com.kesh.tools.ToolResult;

/**
 * Checks whether an answer actually addresses the user ask. Does not authorize tools.
 */
public interface AnswerJudge {

    JudgeVerdict evaluate(String question, String answer, RequestAnalysis analysis, List<ToolResult> tools);

    static AnswerJudge alwaysOk() {
        return (question, answer, analysis, tools) -> JudgeVerdict.pass();
    }

    record JudgeVerdict(boolean ok, String reason, List<CapabilityIntent> retryIntents) {
        public JudgeVerdict {
            retryIntents = retryIntents == null ? List.of() : List.copyOf(retryIntents);
            reason = reason == null ? "" : reason;
        }

        public static JudgeVerdict pass() {
            return new JudgeVerdict(true, "", List.of());
        }

        public static JudgeVerdict retry(String reason, List<CapabilityIntent> retryIntents) {
            return new JudgeVerdict(false, reason, retryIntents);
        }
    }
}
