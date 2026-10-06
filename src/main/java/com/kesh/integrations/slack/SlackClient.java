package com.kesh.integrations.slack;

import java.util.List;

public interface SlackClient {

    List<SlackMessage> getUnreadMessages();

    List<SlackMessage> getMentions();

    SlackThread getThread(String threadId);
}
