/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Ensures the local {@code me} account exists at startup.
 */
@ApplicationScoped
public class UserBootstrap {

    @Inject
    UserService userService;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        userService.findOrCreateMe();
    }
}
