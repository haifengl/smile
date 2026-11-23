/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat.agent;

import java.util.Optional;
import java.util.function.Consumer;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.UnlessBuildProfile;
import smile.auth.AuthProviderKind;
import smile.auth.User;
import smile.chat.CompletionRequest;
import smile.llm.ChatCompletion;

/**
 * Default no-op {@link ChatAgentBridge} used when no server-side agent plugin
 * is staged in {@code serve/plugins/}.
 *
 * @author Haifeng Li
 */
@UnlessBuildProfile("micro")
@DefaultBean
@ApplicationScoped
public class NoOpChatAgentBridge implements ChatAgentBridge {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<OAuthAppCredentials> resolveOAuthCredentials(
            AuthProviderKind provider,
            String customClientId,
            String customClientSecret,
            String customTenantId
    ) {
        if (customClientId != null && !customClientId.isBlank()) {
            String secret = (customClientSecret != null && !customClientSecret.isBlank())
                    ? customClientSecret.trim()
                    : null;
            if (provider == AuthProviderKind.GOOGLE && secret == null) {
                return Optional.empty();
            }
            String tenant = provider == AuthProviderKind.MICROSOFT
                    ? (customTenantId != null && !customTenantId.isBlank() ? customTenantId.trim() : "common")
                    : null;
            return Optional.of(new OAuthAppCredentials(customClientId.trim(), secret, tenant, false));
        }
        return Optional.empty();
    }

    @Override
    public void onOAuthLogin(User user, OAuthLoginPayload payload) {
        // No-op when server-side agent runtime is not packaged
    }

    @Override
    public ChatCompletion complete(
            User user,
            CompletionRequest request,
            int localPort,
            String loadedModel,
            Consumer<String> chunkSink
    ) {
        throw new UnsupportedOperationException("Server-side kirin agent is only enabled in GPU deployments.");
    }
}
