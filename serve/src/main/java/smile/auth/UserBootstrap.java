/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.auth;

import java.util.List;
import io.quarkus.panache.common.Page;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import smile.serve.model.ModelCatalog;

/**
 * Bootstraps {@link UserCache} at startup: preloads existing OIDC users when
 * OIDC mode is active or local {@code me} is disabled, or creates the local
 * {@code me} account when local-me auto-login is enabled.
 */
@ApplicationScoped
public class UserBootstrap {

    private static final int OIDC_PRELOAD_LIMIT = 10_000;

    @Inject
    UserService userService;

    @Inject
    UserCache userCache;

    @Inject
    ModelCatalog modelCatalog;

    @Inject
    OidcAuthProvider oidcAuthProvider;

    @Inject
    AuthConfig authConfig;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        boolean oidcActive = oidcAuthProvider != null && oidcAuthProvider.isActive();
        boolean localMeDisabled = authConfig != null && !authConfig.localMeEnabled();
        if (oidcActive || localMeDisabled) {
            List<User> oidcUsers = User.<User>find("authProvider", AuthProviderKind.OIDC)
                    .page(Page.of(0, OIDC_PRELOAD_LIMIT))
                    .list();
            for (User user : oidcUsers) {
                userCache.register(user);
            }
            return;
        }
        if (modelCatalog.isEmpty()) {
            return;
        }
        userCache.register(userService.findOrCreateMe());
    }
}
