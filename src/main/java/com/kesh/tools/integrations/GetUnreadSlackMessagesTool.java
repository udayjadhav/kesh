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

public class GetUnreadSlackMessagesTool implements Tool {

    private final SlackClient slackClient;

    public GetUnreadSlackMessagesTool(SlackClient slackClient) {
        this.slackClient = slackClient;
    }

    @Override
    public String name() {
        return "get_unread_slack_messages";
    }

    @Override
    public String description() {
        return "Get unread Slack messages from the connected workspace.";
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
        List<SlackMessage> unread = slackClient.getUnreadMessages();
        String body = unread.stream()
                .map(msg -> msg.channel() + " | " + msg.author() + " | "
                        + (msg.mention() ? "MENTION " : "") + msg.text())
                .collect(Collectors.joining("\n"));
        String output = unread.size() + " unread.\n" + body;
        return ToolResult.ok(name(), output, elapsed(started));
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
