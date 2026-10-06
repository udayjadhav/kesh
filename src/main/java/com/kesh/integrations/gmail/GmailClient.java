package com.kesh.integrations.gmail;

import java.util.List;

public interface GmailClient {

    List<EmailMessage> getUnreadMessages();
}
