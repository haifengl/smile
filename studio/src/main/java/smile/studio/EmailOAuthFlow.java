/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Consumer;
import ioa.agent.calendar.CalendarConfig;
import ioa.agent.email.EmailConfig;
import ioa.agent.email.OAuthFlow;

/**
 * Delegating adapter to {@link ioa.agent.email.OAuthFlow} for desktop OAuth 2.0
 * PKCE loopback sign-in ({@code "Login with Google"} and {@code "Login with Microsoft"}),
 * automatic user profile discovery, and dual {@link EmailConfig}/{@link CalendarConfig}
 * account provisioning.
 *
 * <p>All OAuth 2.0 application credentials, endpoint definitions, and encrypted
 * vault operations live in {@code ioa.agent.email.OAuthFlow} and
 * {@code ioa.agent.email.OAuthClientVault}.
 *
 * @author Haifeng Li
 */
public final class EmailOAuthFlow {

    /**
     * Tokens returned upon completing the OAuth 2.0 authorization code exchange.
     *
     * @param refreshToken long-lived OAuth 2.0 refresh token to store in {@code SecretStore}.
     * @param accessToken initial short-lived bearer access token.
     */
    public record OAuthResult(
            String refreshToken,
            String accessToken
    ) { }

    private EmailOAuthFlow() {
    }

    /**
     * Runs the one-click {@code "Login with Google"} flow and returns the discovered
     * user profile and tokens.
     *
     * @param timeout max wait duration for the user to complete sign-in in the browser.
     * @return the acquired {@link OAuthFlow.OAuthLoginResult}.
     * @throws Exception if authorization is denied, times out, or token exchange fails.
     */
    public static OAuthFlow.OAuthLoginResult loginWithGoogle(Duration timeout) throws Exception {
        return OAuthFlow.loginWithGoogle(timeout);
    }

    /**
     * Runs the one-click {@code "Login with Microsoft"} flow and returns the discovered
     * user profile and tokens.
     *
     * @param timeout max wait duration for the user to complete sign-in in the browser.
     * @return the acquired {@link OAuthFlow.OAuthLoginResult}.
     * @throws Exception if authorization is denied, times out, or token exchange fails.
     */
    public static OAuthFlow.OAuthLoginResult loginWithMicrosoft(Duration timeout) throws Exception {
        return OAuthFlow.loginWithMicrosoft(timeout);
    }

    /**
     * Runs the interactive browser PKCE loopback OAuth 2.0 flow for {@code account}.
     *
     * @param account email account specification ({@code GMAIL} or {@code MSGRAPH}).
     * @param clientSecret optional OAuth client secret (null or empty to resolve via {@code OAuthClientVault}).
     * @param timeout max wait duration for the user to complete sign-in in the browser.
     * @return the acquired {@link OAuthResult}.
     * @throws Exception if authorization is denied, times out, or token exchange fails.
     */
    public static OAuthResult authorizePkceLoopback(
            EmailConfig.AccountSpec account,
            String clientSecret,
            Duration timeout
    ) throws Exception {
        OAuthFlow.OAuthResult res = OAuthFlow.authorizePkceLoopback(account, clientSecret, timeout);
        return new OAuthResult(res.refreshToken(), res.accessToken());
    }

    /**
     * Runs the interactive browser PKCE loopback OAuth 2.0 flow for a
     * {@link CalendarConfig.AccountSpec} ({@code GOOGLE} or {@code MSGRAPH}).
     *
     * @param account calendar account specification.
     * @param emailConfig optional email configuration for inherited client/tenant IDs.
     * @param clientSecret optional OAuth client secret.
     * @param timeout max wait duration for the user to complete sign-in in the browser.
     * @return the acquired {@link OAuthResult}.
     * @throws Exception if authorization is denied, times out, or token exchange fails.
     */
    public static OAuthResult authorizeCalendarPkceLoopback(
            CalendarConfig.AccountSpec account,
            EmailConfig emailConfig,
            String clientSecret,
            Duration timeout
    ) throws Exception {
        OAuthFlow.OAuthResult res = OAuthFlow.authorizeCalendarPkceLoopback(
                account, emailConfig, clientSecret, timeout);
        return new OAuthResult(res.refreshToken(), res.accessToken());
    }

    /**
     * Runs the PKCE loopback OAuth 2.0 flow with custom endpoints and browser launcher
     * (used in deterministic unit tests).
     *
     * @param account email account specification.
     * @param clientSecret optional OAuth client secret.
     * @param authEndpoint OAuth authorization endpoint URL.
     * @param tokenEndpoint OAuth token endpoint URL.
     * @param browserLauncher callback receiving the full authorization {@link URI}.
     * @param httpClient HTTP client for token exchange.
     * @param timeout timeout waiting for loopback redirect.
     * @return the acquired {@link OAuthResult}.
     * @throws Exception if the flow fails or times out.
     */
    public static OAuthResult authorizePkceLoopback(
            EmailConfig.AccountSpec account,
            String clientSecret,
            String authEndpoint,
            String tokenEndpoint,
            Consumer<URI> browserLauncher,
            HttpClient httpClient,
            Duration timeout
    ) throws Exception {
        OAuthFlow.OAuthResult res = OAuthFlow.authorizePkceLoopback(
                account, clientSecret, authEndpoint, tokenEndpoint, browserLauncher, httpClient, timeout);
        return new OAuthResult(res.refreshToken(), res.accessToken());
    }

    static String generateCodeVerifier() {
        return OAuthFlow.generateCodeVerifier();
    }

    static String computeS256Challenge(String codeVerifier) throws Exception {
        return OAuthFlow.computeS256Challenge(codeVerifier);
    }
}
