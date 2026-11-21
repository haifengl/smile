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
import smile.auth.AuthProviderKind;
import smile.auth.User;
import smile.chat.CompletionRequest;
import smile.llm.ChatCompletion;

/**
 * SPI bridging SMILE Serve's OAuth authentication and {@code /v1/chat/completions}
 * endpoint to the server-side {@code kirin} personal assistant agent when the
 * {@code ioa-agent} runtime is packaged (specifically in {@code Dockerfile.gpu}).
 *
 * <p>Kept free of {@code ioa.*} imports so non-GPU builds ({@code Dockerfile.micro},
 * {@code Dockerfile.native}, CPU {@code Dockerfile}, and public CI checkouts without
 * {@code studio/lib}) compile and run cleanly using {@link NoOpChatAgentBridge}.
 *
 * @author Haifeng Li
 */
public interface ChatAgentBridge {

    /**
     * Resolved OAuth 2.0 application credentials.
     *
     * @param clientId     OAuth 2.0 application client ID.
     * @param clientSecret optional OAuth 2.0 client secret (null for public clients).
     * @param tenantId     Microsoft Entra tenant ID (e.g. {@code "common"}; null for Google).
     * @param builtIn      {@code true} when resolved from the built-in Studio vault.
     */
    record OAuthAppCredentials(
            String clientId,
            String clientSecret,
            String tenantId,
            boolean builtIn
    ) { }

    /**
     * Completed OAuth 2.0 login and token exchange payload passed to
     * {@link #onOAuthLogin(User, OAuthLoginPayload)} to provision the user's
     * persistent home directory, {@code kirin} agent workspace, and isolated
     * {@code Email} / {@code Calendar} credentials.
     *
     * @param provider       authentication provider ({@link AuthProviderKind#GOOGLE} or {@link AuthProviderKind#MICROSOFT}).
     * @param emailAddress   user's verified email address.
     * @param displayName    user's display name.
     * @param refreshToken   OAuth 2.0 refresh token (or {@code bearer:<accessToken>} fallback).
     * @param accessToken    initial OAuth 2.0 access token.
     * @param customClientId non-null only when a custom OAuth client ID override was used.
     * @param customTenantId custom Microsoft Entra tenant ID (or {@code "common"}).
     */
    record OAuthLoginPayload(
            AuthProviderKind provider,
            String emailAddress,
            String displayName,
            String refreshToken,
            String accessToken,
            String customClientId,
            String customTenantId
    ) { }

    /**
     * Returns {@code true} when the server-side {@code kirin} agent runtime is
     * compiled and active in this deployment.
     *
     * @return {@code true} if {@code kirin} Agent Mode is available.
     */
    boolean isAvailable();

    /**
     * Resolves default OAuth 2.0 application credentials for {@code provider}
     * (consulting explicit overrides, environment variables, and Studio's
     * {@code OAuthClientVault} when available).
     *
     * @param provider           {@link AuthProviderKind#GOOGLE} or {@link AuthProviderKind#MICROSOFT}.
     * @param customClientId     optional configured client ID override.
     * @param customClientSecret optional configured client secret override.
     * @param customTenantId     optional configured Microsoft tenant ID override.
     * @return resolved credentials if available.
     */
    Optional<OAuthAppCredentials> resolveOAuthCredentials(
            AuthProviderKind provider,
            String customClientId,
            String customClientSecret,
            String customTenantId
    );

    /**
     * Provisions the authenticated OAuth user's home directory on {@code /smile}
     * (including {@code .smile/agents/kirin/AGENT.md} and {@code skills/}) and
     * stores their isolated {@code Email} and {@code Calendar} OAuth credentials.
     *
     * @param user    persisted authenticated user.
     * @param payload OAuth login tokens and profile metadata.
     */
    void onOAuthLogin(User user, OAuthLoginPayload payload);

    /**
     * Executes a multi-turn {@code kirin} agent completion for the authenticated
     * OAuth user, streaming assistant text chunks to {@code chunkSink} when non-null.
     *
     * @param user        authenticated OAuth user ({@code GOOGLE} or {@code MICROSOFT}).
     * @param request     incoming chat completion request.
     * @param localPort   local HTTP port of {@code smile-serve} for LLM loopback.
     * @param loadedModel loaded model ID from {@code ChatService}.
     * @param chunkSink   optional consumer receiving streamed text chunks.
     * @return completed {@link ChatCompletion}.
     */
    ChatCompletion complete(
            User user,
            CompletionRequest request,
            int localPort,
            String loadedModel,
            Consumer<String> chunkSink
    );
}
