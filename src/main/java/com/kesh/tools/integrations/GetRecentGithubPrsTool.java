package com.kesh.tools.integrations;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kesh.integrations.github.GitHubClient;
import com.kesh.integrations.github.GitHubPullRequest;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

public class GetRecentGithubPrsTool implements Tool {

    private final GitHubClient gitHubClient;

    public GetRecentGithubPrsTool(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    @Override
    public String name() {
        return "get_recent_github_prs";
    }

    @Override
    public String description() {
        return "Get your open GitHub pull requests.";
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
        List<GitHubPullRequest> prs = gitHubClient.getMyOpenPullRequests();
        String body = prs.stream()
                .map(pr -> pr.repo() + "#" + pr.number() + " " + pr.title() + " [" + pr.status() + "]")
                .collect(Collectors.joining("\n"));
        return ToolResult.ok(name(), body, elapsed(started));
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
