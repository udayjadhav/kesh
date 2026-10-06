package com.kesh.integrations.slack;

public record SlackMessage(
        String id,
        String channel,
        String author,
        String text,
        boolean mention,
        String timestamp
) {
}
