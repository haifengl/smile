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
package smile.studio.license;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import smile.license.LicenseKey;

/**
 * Resolved runtime license state used by {@code SmileStudio}, {@code StatusBar}, and {@code LicenseDialog}.
 *
 * @param mode              active usage mode.
 * @param key               verified cryptographic license key when {@code mode == COMMERCIAL_LICENSED}.
 * @param trialExpiresAt    expiration date of the 30-day commercial trial, if started.
 * @param sourceDescription human-readable description of where the active key was loaded from.
 * @param warningMessage    optional warning banner message (e.g., expired key or expired trial).
 * @author Haifeng Li
 */
public record LicenseState(
        UsageMode mode,
        Optional<LicenseKey> key,
        Optional<LocalDate> trialExpiresAt,
        Optional<String> sourceDescription,
        Optional<String> warningMessage
) {
    /**
     * Compact constructor validating non-null fields.
     */
    public LicenseState {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(trialExpiresAt, "trialExpiresAt");
        Objects.requireNonNull(sourceDescription, "sourceDescription");
        Objects.requireNonNull(warningMessage, "warningMessage");
    }

    /**
     * Returns true when the modal {@code LicenseDialog} must be shown at Studio startup.
     *
     * @param today the current date.
     * @return true if user attestation or license renewal is required at startup.
     */
    public boolean requiresStartupAttestation(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return switch (mode) {
            case UNSET -> true;
            case EVALUATION -> false;
            case COMMERCIAL_TRIAL -> trialExpiresAt.map(today::isAfter).orElse(true);
            case COMMERCIAL_LICENSED -> key.map(k -> k.isExpired(today)).orElse(true);
        };
    }

    /**
     * Returns the number of days remaining in the 30-day commercial trial relative to {@code today},
     * or {@code 0} if no active trial exists.
     *
     * @param today the current date.
     * @return non-negative days remaining in the commercial trial.
     */
    public long trialDaysRemaining(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return trialExpiresAt
                .map(exp -> Math.max(0L, ChronoUnit.DAYS.between(today, exp)))
                .orElse(0L);
    }

    /**
     * Returns true if a 30-day commercial trial was previously started and has already expired.
     *
     * @param today the current date.
     * @return true if the self-service commercial trial has expired.
     */
    public boolean isTrialExpired(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return trialExpiresAt.map(today::isAfter).orElse(false);
    }

    /**
     * Returns true when this state grants access to commercial Studio features
     * (such as Private &amp; Custom Enterprise Catalogs) as of {@link LocalDate#now()}.
     *
     * @return true if backed by an active commercial license or active 30-day commercial trial.
     */
    public boolean isCommercialEntitled() {
        return isCommercialEntitled(LocalDate.now());
    }

    /**
     * Returns true when this state grants access to commercial Studio features
     * (such as Private &amp; Custom Enterprise Catalogs) as of {@code today}.
     *
     * @param today the current date.
     * @return true if backed by an active commercial license or active 30-day commercial trial.
     */
    public boolean isCommercialEntitled(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return switch (mode) {
            case COMMERCIAL_LICENSED -> key.map(k -> k.allowsStudio() && !k.isExpired(today)).orElse(false);
            case COMMERCIAL_TRIAL -> trialExpiresAt.map(exp -> !today.isAfter(exp)).orElse(false);
            case EVALUATION, UNSET -> false;
        };
    }
}
