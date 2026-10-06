package com.kesh.workspace;

import java.util.Locale;
import java.util.Set;

public enum WorkspaceMode {
    PROFESSIONAL("work", "Work"),
    PERSONAL("personal", "Personal");

    public static final Set<String> WORK_MESSAGE_TOOLS = Set.of(
            "get_unread_slack_messages",
            "get_slack_mentions",
            "get_my_jira_issues",
            "search_confluence",
            "get_unread_emails",
            "get_recent_github_prs"
    );
    public static final Set<String> PERSONAL_ONLY_TOOLS = Set.of("dictionary_lookup");

    private final String path;
    private final String label;

    WorkspaceMode(String path, String label) {
        this.path = path;
        this.label = label;
    }

    public String path() {
        return path;
    }

    public String label() {
        return label;
    }

    public static WorkspaceMode fromPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Desk is required");
        }
        String key = path.toLowerCase(Locale.ROOT).trim();
        if ("work".equals(key) || "professional".equals(key)) {
            return PROFESSIONAL;
        }
        if ("personal".equals(key)) {
            return PERSONAL;
        }
        throw new IllegalArgumentException("Unknown desk: " + path);
    }

    public boolean allowsTool(String tool) {
        return tool != null && !tool.isBlank();
    }

    public String intro() {
        return "";
    }
}
