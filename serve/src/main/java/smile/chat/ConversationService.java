/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final DateTimeFormatter DEFAULT_TITLE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US);
    private static final int TITLE_WORD_LIMIT = 6;

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
     * Builds a human-readable placeholder title for a new conversation.
     *
     * @param createdAt conversation creation time.
     * @return formatted timestamp title.
     */
    public static String defaultTitle(Instant createdAt) {
        Instant instant = createdAt != null ? createdAt : Instant.now();
        return DEFAULT_TITLE_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    /**
     * Returns message counts keyed by conversation id.
     *
     * @param conversationIds conversation primary keys.
     * @return counts (missing ids imply zero messages).
     */
    public Map<Long, Long> messageCounts(Collection<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = ConversationItem.getEntityManager()
                .createQuery(
                        "SELECT i.conversationId, COUNT(i) FROM ConversationItem i"
                                + " WHERE i.conversationId IN :ids GROUP BY i.conversationId",
                        Object[].class)
                .setParameter("ids", conversationIds)
                .getResultList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * Assigns the default timestamp title after persistence when none is set.
     *
     * @param conversation persisted conversation.
     */
    public static void ensureDefaultTitle(Conversation conversation) {
        if (conversation.title == null || conversation.title.isBlank()) {
            conversation.title = defaultTitle(conversation.createdAt);
        }
    }

    /**
     * Derives a sidebar title from the first user message text.
     *
     * @param text raw message text or JSON content.
     * @return truncated title or {@code null} when no text is available.
     */
    public static String titleFromFirstMessage(String text) {
        if (text == null || text.isBlank()) {
            return null;
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
            return null;
        }
        String[] words = plain.split(" ");
        if (words.length <= TITLE_WORD_LIMIT) {
            return plain;
        }
        return String.join(" ", java.util.Arrays.copyOf(words, TITLE_WORD_LIMIT));
    }

    /**
     * Updates conversation activity timestamp and auto-title on the first user message.
     *
     * @param conversation entity.
     * @param userMessage  latest user message text for title inference.
     */
    public void touchAfterMessage(Conversation conversation, String userMessage) {
        long userMessages = ConversationItem.count(
                "conversationId = ?1 and role = ?2", conversation.id, "user");
        if (userMessages != 1) {
            return;
        }
        String title = titleFromFirstMessage(userMessage);
        if (title != null) {
            conversation.title = title;
        }
    }
}
