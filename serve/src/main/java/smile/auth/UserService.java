/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import smile.chat.Conversation;
import smile.chat.ConversationContent;

/**
 * User persistence and guest-conversation merge on login.
 */
@ApplicationScoped
public class UserService {
    public static final String LOCAL_ME_EXTERNAL_ID = "me";

    @Inject
    AuthConfig config;

    @Inject
    UserCache userCache;

    /**
     * Returns the bootstrap local {@code me} account, creating it when missing.
     * Race-safe across concurrent multi-replica boots.
     *
     * @return persisted local user.
     */
    @Transactional
    public User findOrCreateMe() {
        User existing = User.findByExternalId(LOCAL_ME_EXTERNAL_ID);
        if (existing != null) {
            if (userCache != null) {
                userCache.register(existing);
            }
            return existing;
        }
        try {
            User created = QuarkusTransaction.requiringNew().call(() -> {
                User again = User.findByExternalId(LOCAL_ME_EXTERNAL_ID);
                if (again != null) {
                    return again;
                }
                User user = new User();
                user.externalId = LOCAL_ME_EXTERNAL_ID;
                user.displayName = "Me";
                user.authProvider = AuthProviderKind.LOCAL;
                user.persistAndFlush();
                return user;
            });
            if (userCache != null) {
                userCache.register(created);
            }
            return created;
        } catch (PersistenceException ex) {
            User winner = QuarkusTransaction.requiringNew().call(() -> User.findByExternalId(LOCAL_ME_EXTERNAL_ID));
            if (winner != null) {
                if (userCache != null) {
                    userCache.register(winner);
                }
                return winner;
            }
            throw ex;
        }
    }

    /**
     * Loads a user by primary key.
     *
     * @param id database id.
     * @return user or {@code null}.
     */
    public User findById(long id) {
        return User.findById(id);
    }

    /**
     * Upserts a Google account from OIDC / OAuth profile claims.
     *
     * @param sub     Google subject.
     * @param email   email address.
     * @param name    display name.
     * @param picture avatar URL.
     * @return persisted user.
     */
    @Transactional
    public User upsertGoogleUser(String sub, String email, String name, String picture) {
        return upsertOAuthUser(AuthProviderKind.GOOGLE, sub, email, name, picture);
    }

    /**
     * Upserts a Microsoft account from Microsoft Graph / Entra OAuth profile claims.
     *
     * @param sub     Microsoft subject identifier ({@code ms:<id>}).
     * @param email   email address.
     * @param name    display name.
     * @param picture avatar URL.
     * @return persisted user.
     */
    @Transactional
    public User upsertMicrosoftUser(String sub, String email, String name, String picture) {
        return upsertOAuthUser(AuthProviderKind.MICROSOFT, sub, email, name, picture);
    }

    /**
     * Upserts an enterprise OIDC user account with collision-free issuer-namespaced
     * external id and column-bounded profile fields.
     *
     * @param externalId issuer-namespaced external id ({@code <= 128} chars).
     * @param email      email address.
     * @param name       display name.
     * @param picture    avatar URL.
     * @return persisted user.
     */
    @Transactional
    public User upsertOidcUser(String externalId, String email, String name, String picture) {
        return upsertOAuthUser(
                AuthProviderKind.OIDC,
                truncate(externalId, 128),
                truncate(email, 256),
                truncate(name, 128),
                truncate(picture, 1024)
        );
    }

    /**
     * Lazily ensures that an OIDC-authenticated request context (for example a
     * stateless M2M Bearer JWT caller accessing conversations, media, or profile)
     * has a persisted {@link User} entity bound to {@code ctx} and indexed in
     * {@link UserCache}. Performs zero database queries when already resolved or cached.
     *
     * @param ctx request-scoped auth context.
     * @return resolved user, or {@code null} when unauthenticated.
     */
    public User ensureAuthenticatedUser(AuthContext ctx) {
        if (ctx == null || !ctx.isOidcAuthenticated()) {
            return ctx == null ? null : ctx.user();
        }
        if (ctx.user() != null) {
            return ctx.user();
        }
        User resolved = ensureOidcUser(ctx.oidcPrincipal());
        if (resolved != null) {
            ctx.setOidcPrincipal(ctx.oidcPrincipal(), resolved);
        }
        return resolved;
    }

    /**
     * Lazily resolves or upserts an {@link OidcPrincipal} into a persisted {@link User}
     * without requiring an active request-scoped {@link AuthContext}. Safe to invoke
     * from async worker threads (such as streaming SSE workers).
     *
     * @param principal verified OIDC / trusted-proxy principal.
     * @return resolved or newly persisted user, or {@code null} when principal is null
     *         or references a deleted session user id.
     */
    public User ensureOidcUser(OidcPrincipal principal) {
        if (principal == null) {
            return null;
        }
        if (principal.sessionUserId() != null) {
            User cachedById = userCache != null ? userCache.getById(principal.sessionUserId()) : null;
            if (cachedById != null && cachedById.authProvider == AuthProviderKind.OIDC) {
                return cachedById;
            }
            User found = QuarkusTransaction.joiningExisting().call(() -> findById(principal.sessionUserId()));
            if (found != null && found.authProvider == AuthProviderKind.OIDC) {
                if (userCache != null) {
                    userCache.register(found);
                }
                return found;
            }
            return null;
        }
        User cached = userCache != null ? userCache.getByExternalId(principal.externalId()) : null;
        if (cached != null) {
            return cached;
        }
        String fallbackName = principal.displayName() != null && !principal.displayName().isBlank()
                ? principal.displayName()
                : (principal.email() != null && !principal.email().isBlank()
                        ? principal.email()
                        : principal.subject());
        return QuarkusTransaction.joiningExisting().call(() ->
                upsertOidcUser(principal.externalId(), principal.email(), fallbackName, principal.avatarUrl()));
    }

    /**
     * Upserts an OAuth / OIDC user account. Race-safe under concurrent first-request
     * upserts for the same {@code external_id}: isolates insert in a new transaction and
     * re-fetches on unique-constraint collision.
     *
     * @param provider {@link AuthProviderKind#GOOGLE}, {@link AuthProviderKind#MICROSOFT}, or {@link AuthProviderKind#OIDC}.
     * @param sub      provider subject / external identifier.
     * @param email    email address.
     * @param name     display name.
     * @param picture  avatar URL.
     * @return persisted user.
     */
    @Transactional
    public User upsertOAuthUser(
            AuthProviderKind provider,
            String sub,
            String email,
            String name,
            String picture
    ) {
        String safeSub = truncate(sub, 128);
        String safeEmail = truncate(email, 256);
        String safeName = truncate(name, 128);
        String safePicture = truncate(picture, 1024);
        User user = User.findByExternalId(safeSub);
        if (user == null) {
            try {
                User inserted = QuarkusTransaction.requiringNew().call(() -> {
                    User again = User.findByExternalId(safeSub);
                    if (again != null) {
                        applyProfileUpdates(again, provider, safeEmail, safeName, safePicture);
                        return again;
                    }
                    User fresh = new User();
                    fresh.externalId = safeSub;
                    fresh.authProvider = provider;
                    fresh.displayName = safeName != null && !safeName.isBlank() ? safeName : "User";
                    fresh.email = safeEmail;
                    fresh.avatarUrl = safePicture;
                    fresh.persistAndFlush();
                    return fresh;
                });
                if (userCache != null) {
                    userCache.register(inserted);
                }
                return inserted;
            } catch (PersistenceException ex) {
                User winner = QuarkusTransaction.requiringNew().call(() -> {
                    User existing = User.findByExternalId(safeSub);
                    if (existing != null) {
                        applyProfileUpdates(existing, provider, safeEmail, safeName, safePicture);
                    }
                    return existing;
                });
                if (winner != null) {
                    if (userCache != null) {
                        userCache.register(winner);
                    }
                    return winner;
                }
                throw ex;
            }
        }
        applyProfileUpdates(user, provider, safeEmail, safeName, safePicture);
        if (userCache != null) {
            userCache.register(user);
        }
        return user;
    }

    private static void applyProfileUpdates(
            User user,
            AuthProviderKind provider,
            String safeEmail,
            String safeName,
            String safePicture
    ) {
        user.authProvider = provider;
        if (safeEmail != null && !safeEmail.isBlank()) {
            user.email = safeEmail;
        }
        if (safeName != null && !safeName.isBlank()) {
            user.displayName = safeName;
        }
        if (safePicture != null && !safePicture.isBlank()) {
            user.avatarUrl = safePicture;
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    /**
     * Assigns recent guest conversations from the same IP to the user.
     *
     * @param userId   authenticated user id.
     * @param clientIp client IP from the login request.
     */
    @Transactional
    public void mergeGuestConversations(long userId, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        Instant since = Instant.now().minus(config.guestMergeHours(), ChronoUnit.HOURS);
        List<Conversation> guestConversations = Conversation.list(
                "userId is null and clientIP = ?1 and createdAt >= ?2", clientIp, since);
        if (guestConversations.isEmpty()) {
            return;
        }
        List<Long> ids = guestConversations.stream().map(c -> c.id).toList();
        for (Conversation c : guestConversations) {
            c.userId = userId;
        }
        ConversationContent.update(
                "ownerUserId = ?1 where conversationId in ?2", userId, ids);
    }
}
