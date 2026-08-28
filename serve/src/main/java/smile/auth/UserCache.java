/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import java.util.concurrent.ConcurrentHashMap;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory user index for auth resolution without blocking I/O threads.
 */
@ApplicationScoped
public class UserCache {
    private final ConcurrentHashMap<Long, User> byId = new ConcurrentHashMap<>();
    private volatile User localMe;

    /**
     * Registers or refreshes a user in the cache.
     *
     * @param user persisted user entity.
     */
    public void register(User user) {
        if (user == null || user.id == null) {
            return;
        }
        byId.put(user.id, user);
        if (UserService.LOCAL_ME_EXTERNAL_ID.equals(user.externalId)) {
            localMe = user;
        }
    }

    /**
     * Returns a cached user by id.
     *
     * @param id database id.
     * @return user or {@code null}.
     */
    public User getById(long id) {
        return byId.get(id);
    }

    /**
     * Returns the bootstrap local {@code me} account when registered.
     *
     * @return local user or {@code null}.
     */
    public User localMe() {
        return localMe;
    }
}
