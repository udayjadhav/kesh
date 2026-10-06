package com.kesh.integrations.jira;

import java.util.List;

public interface JiraClient {

    List<JiraIssue> getMyOpenIssues();

    JiraIssue getIssue(String key);
}
