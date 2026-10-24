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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PublicKey;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;
import photos.sluice.secrets.SecretId;
import photos.sluice.secrets.SecretStore;
import smile.license.LicenseKey;
import smile.license.LicenseVerificationException;
import smile.license.LicenseVerificationException.Reason;
import smile.license.LicenseVerifier;
import smile.license.LicenseVerifier.ResolvedToken;

/**
 * Resolves, stores, and transitions SMILE Agent Studio license keys, usage modes,
 * and 30-day commercial trial timestamps across environment variables, system/user
 * files, {@link SecretStore}, and {@link Preferences}.
 *
 * @author Haifeng Li
 */
public final class LicenseStore {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LicenseStore.class);

    /** SecretStore identifier for the commercial license token. */
    public static final SecretId LICENSE_SECRET = new SecretId("halo-license", "SMILE_LICENSE_KEY");

    /** Preferences key for the attested usage mode. */
    public static final String PREF_USAGE_MODE = "license.usageMode";

    /** Preferences key for the date the usage mode was attested. */
    public static final String PREF_ATTESTED_AT = "license.attestedAt";

    /** Preferences key for the start date of the 30-day commercial trial. */
    public static final String PREF_TRIAL_STARTED_AT = "license.trialStartedAt";

    /** Duration of the self-service commercial production trial in calendar days. */
    public static final int TRIAL_DURATION_DAYS = 30;

    private final Preferences prefs;
    private final SecretStore secrets;
    private final Path smileHome;
    private final Path userHome;
    private final PublicKey publicKey;
    private String envTokenOverride;
    private String propTokenOverride;
    private boolean useSystemEnvAndProps = true;

    /**
     * Creates a {@link LicenseStore} backed by the given preferences and secret store,
     * reading system environment variables, system properties, {@code smile.home},
     * and {@code user.home}.
     *
     * @param prefs   application preferences.
     * @param secrets application secret store.
     */
    public LicenseStore(Preferences prefs, SecretStore secrets) {
        this(
                prefs,
                secrets,
                resolveSmileHome(),
                Path.of(System.getProperty("user.home")),
                LicenseVerifier.defaultPublicKey()
        );
    }

    /**
     * Creates a {@link LicenseStore} with explicit directory and public-key dependencies
     * (used by unit tests).
     *
     * @param prefs     application preferences.
     * @param secrets   application secret store (nullable).
     * @param smileHome path to {@code $SMILE_HOME} (nullable).
     * @param userHome  path to user home directory.
     * @param publicKey Ed25519 public key used to verify tokens.
     */
    public LicenseStore(
            Preferences prefs,
            SecretStore secrets,
            Path smileHome,
            Path userHome,
            PublicKey publicKey) {
        this.prefs = Objects.requireNonNull(prefs, "prefs");
        this.secrets = secrets;
        this.smileHome = smileHome;
        this.userHome = Objects.requireNonNull(userHome, "userHome");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
    }

    /**
     * Configures explicit environment and system-property overrides for deterministic testing.
     *
     * @param envToken  simulated {@code SMILE_LICENSE_KEY} value (nullable).
     * @param propToken simulated {@code -Dsmile.license.key} value (nullable).
     * @return this store for chaining.
     */
    public LicenseStore withExternalTokens(String envToken, String propToken) {
        this.envTokenOverride = envToken;
        this.propTokenOverride = propToken;
        this.useSystemEnvAndProps = false;
        return this;
    }

    private static Path resolveSmileHome() {
        String prop = System.getProperty("smile.home");
        if (prop == null || prop.isBlank()) {
            prop = System.getenv("SMILE_HOME");
        }
        return (prop != null && !prop.isBlank()) ? Path.of(prop) : null;
    }

    /**
     * Returns the path to the per-user license key file ({@code ~/.smile/license.key}).
     *
     * @return the user license key file path.
     */
    public Path userLicenseFile() {
        return userHome.resolve(".smile").resolve("license.key");
    }

    /**
     * Returns the path to the per-user trial timestamp backup file ({@code ~/.smile/license-trial.txt}).
     *
     * @return the trial timestamp file path.
     */
    public Path userTrialFile() {
        return userHome.resolve(".smile").resolve("license-trial.txt");
    }

    /**
     * Loads and resolves the current {@link LicenseState} as of {@link LocalDate#now()}.
     *
     * @return the resolved {@link LicenseState}.
     */
    public LicenseState load() {
        return load(LocalDate.now());
    }

    /**
     * Loads and resolves the current {@link LicenseState} as of {@code today}.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Pre-deployed external key ({@code SMILE_LICENSE_KEY} env → {@code -Dsmile.license.key} →
     *       {@code $SMILE_HOME/conf/license.key}) always takes precedence for zero-touch enterprise IT boot.</li>
     *   <li>If the user explicitly selected {@link UsageMode#EVALUATION} in {@code Preferences}
     *       (and no IT env/sysprop/system-conf key is overriding), honor {@code EVALUATION}.</li>
     *   <li>User license file ({@code ~/.smile/license.key}) or {@link SecretStore} key.</li>
     *   <li>{@link UsageMode#COMMERCIAL_TRIAL} if active or expired.</li>
     *   <li>{@link UsageMode#UNSET} on first run.</li>
     * </ol>
     *
     * @param today the date to evaluate expiration against.
     * @return the resolved {@link LicenseState}.
     */
    public LicenseState load(LocalDate today) {
        Objects.requireNonNull(today, "today");
        Optional<LocalDate> trialStarted = readTrialStartDate();
        Optional<LocalDate> trialExpires = trialStarted.map(d -> d.plusDays(TRIAL_DURATION_DAYS));
        UsageMode savedMode = readSavedMode();

        String envVal = useSystemEnvAndProps ? System.getenv(LicenseVerifier.ENV_LICENSE_KEY) : envTokenOverride;
        String propVal = useSystemEnvAndProps ? System.getProperty(LicenseVerifier.PROP_LICENSE_KEY) : propTokenOverride;

        // 1. Check high-priority IT provisioning sources (env, sysprop, $SMILE_HOME/conf/license.key)
        Optional<ResolvedToken> itToken = LicenseVerifier.resolveExternalToken(envVal, propVal, smileHome, null);
        if (itToken.isPresent()) {
            return evaluateCandidateToken(itToken.get(), trialExpires, today);
        }

        // 2. If user explicitly chose Evaluation Mode (and no IT-level override is set), honor it
        if (savedMode == UsageMode.EVALUATION) {
            return new LicenseState(
                    UsageMode.EVALUATION,
                    Optional.empty(),
                    trialExpires,
                    Optional.empty(),
                    Optional.empty()
            );
        }

        // 3. Check per-user key locations (~/.smile/license.key, then SecretStore)
        Optional<ResolvedToken> userToken = LicenseVerifier.resolveExternalToken(null, null, null, userHome);
        if (userToken.isEmpty()) {
            userToken = readSecretStoreToken();
        }
        if (userToken.isPresent()) {
            return evaluateCandidateToken(userToken.get(), trialExpires, today);
        }

        // 4. No key present: check saved mode (COMMERCIAL_TRIAL or COMMERCIAL_LICENSED with missing key)
        if (savedMode == UsageMode.COMMERCIAL_TRIAL && trialExpires.isPresent()) {
            LocalDate exp = trialExpires.get();
            if (today.isAfter(exp)) {
                return new LicenseState(
                        UsageMode.COMMERCIAL_TRIAL,
                        Optional.empty(),
                        trialExpires,
                        Optional.empty(),
                        Optional.of("TrialExpired:" + exp)
                );
            }
            return new LicenseState(
                    UsageMode.COMMERCIAL_TRIAL,
                    Optional.empty(),
                    trialExpires,
                    Optional.empty(),
                    Optional.empty()
            );
        }

        return new LicenseState(
                UsageMode.UNSET,
                Optional.empty(),
                trialExpires,
                Optional.empty(),
                Optional.empty()
        );
    }

    private LicenseState evaluateCandidateToken(
            ResolvedToken resolved,
            Optional<LocalDate> trialExpires,
            LocalDate today) {
        try {
            LicenseKey key = LicenseVerifier.verify(resolved.rawToken(), publicKey);
            if (!key.allowsStudio()) {
                return new LicenseState(
                        UsageMode.UNSET,
                        Optional.empty(),
                        trialExpires,
                        Optional.of(resolved.sourceDescription()),
                        Optional.of("NoStudioSeats")
                );
            }
            if (key.isExpired(today)) {
                return new LicenseState(
                        UsageMode.COMMERCIAL_LICENSED,
                        Optional.of(key),
                        trialExpires,
                        Optional.of(resolved.sourceDescription()),
                        Optional.of("KeyExpired:" + key.expiresAt())
                );
            }
            return new LicenseState(
                    UsageMode.COMMERCIAL_LICENSED,
                    Optional.of(key),
                    trialExpires,
                    Optional.of(resolved.sourceDescription()),
                    Optional.empty()
            );
        } catch (LicenseVerificationException ex) {
            logger.warn("Invalid license key at {}: {}", resolved.sourceDescription(), ex.getMessage());
            return new LicenseState(
                    UsageMode.UNSET,
                    Optional.empty(),
                    trialExpires,
                    Optional.of(resolved.sourceDescription()),
                    Optional.of("InvalidKey:" + ex.reason().name())
            );
        }
    }

    /**
     * Verifies and activates a commercial license key interactively, saving it to
     * {@code ~/.smile/license.key}, {@link SecretStore}, and {@link Preferences}.
     *
     * @param rawToken the raw token pasted by the user.
     * @return the updated {@link LicenseState}.
     * @throws LicenseVerificationException if the token is empty, malformed, signature-invalid,
     *                                      has zero Studio seats, or is already expired.
     */
    public LicenseState activateKey(String rawToken) throws LicenseVerificationException {
        return activateKey(rawToken, LocalDate.now());
    }

    /**
     * Verifies and activates a commercial license key as of {@code today}.
     *
     * @param rawToken the raw token pasted by the user.
     * @param today    the current date to check expiration against.
     * @return the updated {@link LicenseState}.
     * @throws LicenseVerificationException if verification fails or the key is expired.
     */
    public LicenseState activateKey(String rawToken, LocalDate today) throws LicenseVerificationException {
        Objects.requireNonNull(today, "today");
        LicenseKey key = LicenseVerifier.verify(rawToken, publicKey);
        if (!key.allowsStudio()) {
            throw new LicenseVerificationException(
                    Reason.INVALID_PAYLOAD,
                    "This license key does not include any SMILE Agent Studio seats (seats=" + key.seats() + ").");
        }
        if (key.isExpired(today)) {
            throw new LicenseVerificationException(
                    Reason.INVALID_PAYLOAD,
                    "License key expired on " + key.expiresAt() + ".");
        }

        Path file = userLicenseFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, key.rawToken() + System.lineSeparator(), StandardCharsets.UTF_8);
            setOwnerOnlyPermissions(file);
        } catch (IOException ex) {
            logger.warn("Could not write license key to {}: {}", file, ex.getMessage());
        }

        if (secrets != null) {
            try {
                if (key.rawToken().length() <= SecretStore.maxSecret()
                        && secrets.whereASaveWouldStoreIt().isPresent()) {
                    secrets.save(LICENSE_SECRET, key.rawToken());
                }
            } catch (RuntimeException ex) {
                logger.debug("Could not store license key in SecretStore: {}", ex.getMessage());
            }
        }

        prefs.put(PREF_USAGE_MODE, UsageMode.COMMERCIAL_LICENSED.name());
        prefs.put(PREF_ATTESTED_AT, today.toString());
        flushPrefs();

        return new LicenseState(
                UsageMode.COMMERCIAL_LICENSED,
                Optional.of(key),
                readTrialStartDate().map(d -> d.plusDays(TRIAL_DURATION_DAYS)),
                Optional.of(file.toString()),
                Optional.empty()
        );
    }

    /**
     * Records the user's attestation that Studio is being used for Personal, Academic,
     * or Non-Production Evaluation under BSL 1.1.
     *
     * @return the updated {@link LicenseState}.
     */
    public LicenseState selectEvaluationMode() {
        return selectEvaluationMode(LocalDate.now());
    }

    /**
     * Records the user's attestation for Evaluation Mode as of {@code today}.
     *
     * @param today the current date.
     * @return the updated {@link LicenseState}.
     */
    public LicenseState selectEvaluationMode(LocalDate today) {
        Objects.requireNonNull(today, "today");
        prefs.put(PREF_USAGE_MODE, UsageMode.EVALUATION.name());
        prefs.put(PREF_ATTESTED_AT, today.toString());
        flushPrefs();
        return new LicenseState(
                UsageMode.EVALUATION,
                Optional.empty(),
                readTrialStartDate().map(d -> d.plusDays(TRIAL_DURATION_DAYS)),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Starts or resumes the 30-day Commercial Production Trial as of {@link LocalDate#now()}.
     *
     * @return the updated {@link LicenseState}.
     * @throws IllegalStateException if a previous 30-day trial on this machine has already expired.
     */
    public LicenseState startCommercialTrial() {
        return startCommercialTrial(LocalDate.now());
    }

    /**
     * Starts or resumes the 30-day Commercial Production Trial as of {@code today}.
     *
     * @param today the current date.
     * @return the updated {@link LicenseState}.
     * @throws IllegalStateException if a previous 30-day trial on this machine has already expired.
     */
    public LicenseState startCommercialTrial(LocalDate today) {
        Objects.requireNonNull(today, "today");
        Optional<LocalDate> existingStart = readTrialStartDate();
        LocalDate startDate = existingStart.orElse(today);
        LocalDate expiresAt = startDate.plusDays(TRIAL_DURATION_DAYS);
        if (today.isAfter(expiresAt)) {
            throw new IllegalStateException("The 30-day commercial trial expired on " + expiresAt + ".");
        }

        if (existingStart.isEmpty()) {
            prefs.put(PREF_TRIAL_STARTED_AT, startDate.toString());
            Path trialFile = userTrialFile();
            try {
                Files.createDirectories(trialFile.getParent());
                Files.writeString(trialFile, startDate.toString() + System.lineSeparator(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                logger.debug("Could not write trial start timestamp to {}: {}", trialFile, ex.getMessage());
            }
        }

        prefs.put(PREF_USAGE_MODE, UsageMode.COMMERCIAL_TRIAL.name());
        prefs.put(PREF_ATTESTED_AT, today.toString());
        flushPrefs();

        return new LicenseState(
                UsageMode.COMMERCIAL_TRIAL,
                Optional.empty(),
                Optional.of(expiresAt),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Removes any user-stored commercial license key from {@code ~/.smile/license.key}
     * and {@link SecretStore}, and transitions to {@link UsageMode#EVALUATION}.
     *
     * @return the updated {@link LicenseState}.
     */
    public LicenseState deactivateKey() {
        return deactivateKey(LocalDate.now());
    }

    /**
     * Removes any user-stored commercial license key as of {@code today} and transitions
     * to {@link UsageMode#EVALUATION}.
     *
     * @param today the current date.
     * @return the updated {@link LicenseState}.
     */
    public LicenseState deactivateKey(LocalDate today) {
        Objects.requireNonNull(today, "today");
        try {
            Files.deleteIfExists(userLicenseFile());
        } catch (IOException ex) {
            logger.warn("Could not delete {}: {}", userLicenseFile(), ex.getMessage());
        }
        if (secrets != null) {
            try {
                secrets.remove(LICENSE_SECRET);
            } catch (RuntimeException ex) {
                logger.debug("Could not delete license key from SecretStore: {}", ex.getMessage());
            }
        }
        return selectEvaluationMode(today);
    }

    private UsageMode readSavedMode() {
        String raw = prefs.get(PREF_USAGE_MODE, null);
        if (raw == null || raw.isBlank()) {
            return UsageMode.UNSET;
        }
        try {
            return UsageMode.valueOf(raw.strip());
        } catch (IllegalArgumentException ex) {
            return UsageMode.UNSET;
        }
    }

    private Optional<LocalDate> readTrialStartDate() {
        String prefVal = prefs.get(PREF_TRIAL_STARTED_AT, null);
        if (prefVal != null && !prefVal.isBlank()) {
            try {
                return Optional.of(LocalDate.parse(prefVal.strip()));
            } catch (DateTimeParseException ignored) {
            }
        }
        Path trialFile = userTrialFile();
        if (Files.isRegularFile(trialFile)) {
            try {
                String content = Files.readString(trialFile, StandardCharsets.UTF_8).strip();
                if (!content.isEmpty()) {
                    LocalDate parsed = LocalDate.parse(content);
                    prefs.put(PREF_TRIAL_STARTED_AT, parsed.toString());
                    return Optional.of(parsed);
                }
            } catch (IOException | DateTimeParseException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedToken> readSecretStoreToken() {
        if (secrets == null) {
            return Optional.empty();
        }
        try {
            return secrets.secret(LICENSE_SECRET)
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .map(s -> new ResolvedToken(s, "SecretStore"));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private void setOwnerOnlyPermissions(Path file) {
        try {
            PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (e.g., Windows NTFS)
        }
    }

    private void flushPrefs() {
        try {
            prefs.flush();
        } catch (Exception ex) {
            logger.debug("Failed to flush license preferences: {}", ex.getMessage());
        }
    }
}
