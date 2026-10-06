package com.kesh.integrations.jira;

public record JiraIssue(String key, String summary, String status, String assignee, String priority) {
}
