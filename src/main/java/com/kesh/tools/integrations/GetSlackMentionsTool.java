package com.kesh.tools.integrations;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kesh.integrations.slack.SlackClient;
import com.kesh.integrations.slack.SlackMessage;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

public class GetSlackMentionsTool implements Tool {

    private final SlackClient slackClient;

    public GetSlackMentionsTool(SlackClient slackClient) {
        this.slackClient = slackClient;
    }

    @Override
    public String name() {
        return "get_slack_mentions";
    }

    @Override
    public String description() {
        return "Get Slack messages that mention you.";
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
        List<SlackMessage> mentions = slackClient.getMentions();
        String body = mentions.stream()
                .map(msg -> msg.channel() + " | " + msg.author() + " | " + msg.text())
                .collect(Collectors.joining("\n"));
        return ToolResult.ok(name(), "Mentions (" + mentions.size() + "):\n" + body, elapsed(started));
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
