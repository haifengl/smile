/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import smile.chat.Conversation;

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
     *
     * @return persisted local user.
     */
    @Transactional
    public User findOrCreateMe() {
        User existing = User.findByExternalId(LOCAL_ME_EXTERNAL_ID);
        if (existing != null) {
            userCache.register(existing);
            return existing;
        }
        User user = new User();
        user.externalId = LOCAL_ME_EXTERNAL_ID;
        user.displayName = "Me";
        user.authProvider = AuthProviderKind.LOCAL;
        user.persist();
        userCache.register(user);
        return user;
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
        User user = User.findByExternalId(sub);
        if (user == null) {
            user = new User();
            user.externalId = sub;
            user.authProvider = AuthProviderKind.GOOGLE;
            user.displayName = name != null && !name.isBlank() ? name : "User";
            user.email = email;
            user.avatarUrl = picture;
            user.persist();
            userCache.register(user);
            return user;
        }
        if (email != null && !email.isBlank()) {
            user.email = email;
        }
        if (name != null && !name.isBlank()) {
            user.displayName = name;
        }
        if (picture != null && !picture.isBlank()) {
            user.avatarUrl = picture;
        }
        userCache.register(user);
        return user;
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
        Conversation.update(
                "userId = ?1 where userId is null and clientIP = ?2 and createdAt >= ?3",
                userId, clientIp, since);
    }
}
