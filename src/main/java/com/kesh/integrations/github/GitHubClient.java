package com.kesh.integrations.github;

import java.util.List;

public interface GitHubClient {

    List<GitHubPullRequest> getMyOpenPullRequests();
}
