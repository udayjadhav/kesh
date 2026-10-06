package com.kesh.api;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kesh.learning.LearningEventStore;
import com.kesh.workspace.WorkspaceMode;

@Controller
@RequestMapping("/{desk:work|personal}")
public class LearningReviewController {

    private final LearningEventStore store;

    public LearningReviewController(LearningEventStore store) {
        this.store = store;
    }

    @GetMapping("/learning")
    public String review(@PathVariable String desk, Model model) {
        WorkspaceMode mode = WorkspaceMode.fromPath(desk);
        model.addAttribute("desk", mode.path());
        model.addAttribute("deskLabel", mode.label());
        model.addAttribute("candidates", store.pendingCandidates(mode.path(), 50));
        model.addAttribute("approved", store.approvedIntents(mode.path(), 20));
        return "learning";
    }

    @PostMapping("/api/learning/{id}/approve")
    @ResponseBody
    public Map<String, Object> approve(
            @PathVariable String desk,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        WorkspaceMode mode = WorkspaceMode.fromPath(desk);
        String intent = body == null || body.get("correctIntent") == null
                ? ""
                : body.get("correctIntent").toString();
        boolean ok = store.approve(mode.path(), id, intent);
        return Map.of("ok", ok);
    }

    @PostMapping("/api/learning/{id}/reject")
    @ResponseBody
    public Map<String, Object> reject(@PathVariable String desk, @PathVariable String id) {
        WorkspaceMode mode = WorkspaceMode.fromPath(desk);
        return Map.of("ok", store.reject(mode.path(), id));
    }

    @GetMapping("/api/learning/candidates")
    @ResponseBody
    public List<?> candidates(@PathVariable String desk) {
        return store.pendingCandidates(WorkspaceMode.fromPath(desk).path(), 50);
    }
}
