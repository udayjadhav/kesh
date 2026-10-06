package com.kesh.tools.integrations;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kesh.integrations.gmail.EmailMessage;
import com.kesh.integrations.gmail.GmailClient;
import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

public class GetUnreadEmailsTool implements Tool {

    private final GmailClient gmailClient;

    public GetUnreadEmailsTool(GmailClient gmailClient) {
        this.gmailClient = gmailClient;
    }

    @Override
    public String name() {
        return "get_unread_emails";
    }

    @Override
    public String description() {
        return "Get unread email.";
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
        List<EmailMessage> unread = gmailClient.getUnreadMessages();
        String body = unread.stream()
                .map(mail -> mail.from() + " | " + mail.subject() + " | " + mail.snippet())
                .collect(Collectors.joining("\n"));
        return ToolResult.ok(name(), body, elapsed(started));
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
