package com.kesh.integrations.github;

public record GitHubPullRequest(String repo, int number, String title, String author, String status) {
}
