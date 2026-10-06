package com.kesh.tools.integrations;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kesh.integrations.jira.JiraClient;
import com.kesh.integrations.jira.JiraIssue;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

public class GetMyJiraIssuesTool implements Tool {

    private final JiraClient jiraClient;

    public GetMyJiraIssuesTool(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    public String name() {
        return "get_my_jira_issues";
    }

    @Override
    public String description() {
        return "Get your open Jira issues.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(Map.of(), List.of());
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long started = System.nanoTime();
        List<JiraIssue> issues = jiraClient.getMyOpenIssues();
        String body = issues.stream()
                .map(issue -> issue.key() + " [" + issue.status() + "] " + issue.summary() + " (" + issue.priority() + ")")
                .collect(Collectors.joining("\n"));
        return ToolResult.ok(name(), body, elapsed(started));
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
