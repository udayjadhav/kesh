package com.kesh.integrations.gmail;

public record EmailMessage(String id, String from, String subject, String snippet, boolean unread) {
}
