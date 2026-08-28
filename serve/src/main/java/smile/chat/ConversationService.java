/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.chat;

import java.util.List;
import java.util.Locale;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import smile.auth.AuthContext;
import smile.chat.blob.ClientIdentity;

/**
 * Conversation ownership, access control, search, and title helpers.
 */
@ApplicationScoped
public class ConversationService {

    @Inject
    AuthContext authContext;

    /**
     * Ensures the caller may access the conversation.
     *
     * @param conversation entity.
     * @param clientIp     request client IP for guest access.
     * @throws NotFoundException when access denied (same as not found).
     */
    public void ensureAccess(Conversation conversation, String clientIp) {
        if (conversation == null) {
            throw new NotFoundException("Conversation not found");
        }
        Long uid = authContext.userId();
        if (conversation.userId != null) {
            if (uid == null || !conversation.userId.equals(uid)) {
                throw new NotFoundException("Conversation not found");
            }
            return;
        }
        // Guest-owned: match IP
        String normalized = ClientIdentity.normalizeIp(clientIp);
        String stored = ClientIdentity.normalizeIp(conversation.clientIP);
        if (!normalized.equals(stored)) {
            throw new NotFoundException("Conversation not found");
        }
    }

    /**
     * Lists conversations for the authenticated user with optional search and pin filter.
     *
     * @param pageIndex page index.
     * @param pageSize  page size.
     * @param query     optional search (title + message content).
     * @param pinned    when non-null, filter by pin state.
     * @return matching conversations.
     */
    public List<Conversation> listForUser(int pageIndex, int pageSize, String query, Boolean pinned) {
        authContext.requireUser();
        Long uid = authContext.userId();
        String q = query == null ? null : query.trim().toLowerCase(Locale.ROOT);
        if (q != null && q.isEmpty()) {
            q = null;
        }
        Sort sort = Sort.by("updatedAt").descending();
        if (q == null && pinned == null) {
            return Conversation.find("userId", sort, uid)
                    .page(Page.of(pageIndex, pageSize))
                    .list();
        }
        if (q == null) {
            return Conversation.find("userId = ?1 and pinned = ?2", sort, uid, pinned)
                    .page(Page.of(pageIndex, pageSize))
                    .list();
        }
        String pattern = "%" + q + "%";
        if (pinned == null) {
            return Conversation.find(
                    "select distinct c from Conversation c left join ConversationItem i"
                            + " on i.conversationId = c.id where c.userId = ?1"
                            + " and (lower(c.title) like ?2 or lower(i.content) like ?2)",
                    sort, uid, pattern)
                    .page(Page.of(pageIndex, pageSize))
                    .list();
        }
        return Conversation.find(
                "select distinct c from Conversation c left join ConversationItem i"
                        + " on i.conversationId = c.id where c.userId = ?1 and c.pinned = ?2"
                        + " and (lower(c.title) like ?3 or lower(i.content) like ?3)",
                sort, uid, pinned, pattern)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    /**
     * Derives a sidebar title from the first user message text.
     *
     * @param text raw message text or JSON content.
     * @return truncated title or {@code "New chat"}.
     */
    public static String titleFromFirstMessage(String text) {
        if (text == null || text.isBlank()) {
            return "New chat";
        }
        String plain = text.trim();
        if (plain.startsWith("[")) {
            // content-parts JSON — best-effort first text part
            int idx = plain.indexOf("\"text\"");
            if (idx >= 0) {
                int colon = plain.indexOf(':', idx);
                int start = plain.indexOf('"', colon + 1);
                int end = start > 0 ? plain.indexOf('"', start + 1) : -1;
                if (start > 0 && end > start) {
                    plain = plain.substring(start + 1, end);
                }
            }
        }
        plain = plain.replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) {
            return "New chat";
        }
        return plain.length() > 60 ? plain.substring(0, 57) + "..." : plain;
    }

    /**
     * Updates conversation activity timestamp and auto-title when missing.
     *
     * @param conversation entity.
     * @param userMessage  latest user message text for title inference.
     */
    public void touchAfterMessage(Conversation conversation, String userMessage) {
        if (conversation.title == null || conversation.title.isBlank()) {
            conversation.title = titleFromFirstMessage(userMessage);
        }
        // updatedAt handled by @UpdateTimestamp on flush
    }
}
