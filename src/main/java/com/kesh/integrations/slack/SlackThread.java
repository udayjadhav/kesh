package com.kesh.integrations.slack;

import java.util.List;

public record SlackThread(String id, String channel, List<SlackMessage> messages) {
}
