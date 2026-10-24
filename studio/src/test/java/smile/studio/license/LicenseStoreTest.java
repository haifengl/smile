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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import smile.license.LicenseTier;
import smile.license.LicenseVerificationException;
import smile.license.LicenseVerificationException.Reason;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LicenseStore} and {@link LicenseState}.
 *
 * @author Haifeng Li
 */
public class LicenseStoreTest {
    private static KeyPair keyPair;

    @TempDir
    Path tempDir;

    private Preferences prefs;
    private Path smileHome;
    private Path userHome;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        keyPair = kpg.generateKeyPair();
    }

    @BeforeEach
    void setUp() throws Exception {
        prefs = Preferences.userRoot().node("smile-test-license-" + UUID.randomUUID());
        smileHome = Files.createDirectories(tempDir.resolve("smile-home"));
        userHome = Files.createDirectories(tempDir.resolve("user-home"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (prefs != null) {
            prefs.removeNode();
        }
    }

    private LicenseStore newStore() {
        return new LicenseStore(prefs, null, smileHome, userHome, keyPair.getPublic())
                .withExternalTokens(null, null);
    }

    private static String signToken(String json) throws Exception {
        byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(payloadBytes);
        byte[] sigBytes = sig.sign();
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return "SMILE-v1." + enc.encodeToString(payloadBytes) + "." + enc.encodeToString(sigBytes);
    }

    @Test
    void firstRunDefaultsToUnsetAndRequiresStartupAttestation() {
        // Given a fresh LicenseStore with no preferences or files
        LicenseStore store = newStore();
        LocalDate today = LocalDate.of(2026, 4, 21);

        // When resolved
        LicenseState state = store.load(today);

        // Then mode is UNSET and startup attestation is required
        assertEquals(UsageMode.UNSET, state.mode());
        assertTrue(state.requiresStartupAttestation(today));
        assertTrue(state.key().isEmpty());
        assertTrue(state.warningMessage().isEmpty());
    }

    @Test
    void selectEvaluationModePersistsAndSkipsStartupGate() {
        // Given a fresh store
        LicenseStore store = newStore();
        LocalDate today = LocalDate.of(2026, 4, 21);

        // When user selects Evaluation Mode
        LicenseState attested = store.selectEvaluationMode(today);

        // Then state is EVALUATION and subsequent loads do not require startup attestation
        assertEquals(UsageMode.EVALUATION, attested.mode());
        assertFalse(attested.requiresStartupAttestation(today));

        LicenseState reloaded = store.load(today.plusDays(10));
        assertEquals(UsageMode.EVALUATION, reloaded.mode());
        assertFalse(reloaded.requiresStartupAttestation(today.plusDays(10)));
    }

    @Test
    void activateValidCommercialKeyWritesFileAndResolvesCommercialLicensed() throws Exception {
        // Given a valid signed STUDIO_TEAM key
        LicenseStore store = newStore();
        LocalDate today = LocalDate.of(2026, 4, 21);
        String token = signToken("""
                {"ver":1,"id":"LIC-2026-0100","customer":"Acme Analytics","email":"ops@acme.com","tier":"STUDIO_TEAM","seats":10,"issuedAt":"2026-04-01","expiresAt":"2027-04-01"}""");

        // When activated
        LicenseState state = store.activateKey(token, today);

        // Then mode is COMMERCIAL_LICENSED, key file is written, and reload succeeds
        assertEquals(UsageMode.COMMERCIAL_LICENSED, state.mode());
        assertFalse(state.requiresStartupAttestation(today));
        assertTrue(Files.isRegularFile(store.userLicenseFile()));
        assertEquals("Acme Analytics", state.key().orElseThrow().customer());
        assertEquals(LicenseTier.STUDIO_TEAM, state.key().orElseThrow().tier());
        assertEquals(10, state.key().orElseThrow().seats());

        LicenseState reloaded = store.load(today);
        assertEquals(UsageMode.COMMERCIAL_LICENSED, reloaded.mode());
        assertEquals("Acme Analytics", reloaded.key().orElseThrow().customer());
    }

    @Test
    void deactivateKeyDeletesFileAndTransitionsToEvaluation() throws Exception {
        // Given an activated commercial license
        LicenseStore store = newStore();
        LocalDate today = LocalDate.of(2026, 4, 21);
        String token = signToken("""
                {"ver":1,"id":"LIC-2026-0101","customer":"Acme Analytics","email":"ops@acme.com","tier":"ENTERPRISE","seats":25,"issuedAt":"2026-04-01","expiresAt":"2027-04-01"}""");
        store.activateKey(token, today);
        assertTrue(Files.exists(store.userLicenseFile()));

        // When deactivated
        LicenseState after = store.deactivateKey(today);

        // Then file is deleted and mode is EVALUATION
        assertFalse(Files.exists(store.userLicenseFile()));
        assertEquals(UsageMode.EVALUATION, after.mode());
        assertEquals(UsageMode.EVALUATION, store.load(today).mode());
    }

    @Test
    void activateRuntimeOnlyKeyWithZeroSeatsIsRejected() throws Exception {
        // Given a RUNTIME_PRO key with seats=0
        LicenseStore store = newStore();
        LocalDate today = LocalDate.of(2026, 4, 21);
        String token = signToken("""
                {"ver":1,"id":"LIC-2026-0102","customer":"Headless Corp","email":"ops@headless.io","tier":"RUNTIME_PRO","seats":0,"issuedAt":"2026-04-01","expiresAt":"2027-04-01"}""");

        // When activated in Studio
        LicenseVerificationException ex = assertThrows(
                LicenseVerificationException.class,
                () -> store.activateKey(token, today));

        // Then rejected with INVALID_PAYLOAD
        assertEquals(Reason.INVALID_PAYLOAD, ex.reason());
    }

    @Test
    void startCommercialTrialRunsFor30DaysAndBlocksResetAfterExpiry() {
        // Given a fresh store
        LicenseStore store = newStore();
        LocalDate start = LocalDate.of(2026, 4, 1);

        // When starting the 30-day commercial trial
        LicenseState active = store.startCommercialTrial(start);

        // Then trial is active with 30 days remaining
        assertEquals(UsageMode.COMMERCIAL_TRIAL, active.mode());
        assertFalse(active.requiresStartupAttestation(start));
        assertEquals(30L, active.trialDaysRemaining(start));
        assertTrue(Files.isRegularFile(store.userTrialFile()));

        // When 10 days pass, even if preferences are cleared, backup file preserves start date
        prefs.remove(LicenseStore.PREF_TRIAL_STARTED_AT);
        LicenseState resumed = store.startCommercialTrial(start.plusDays(10));
        assertEquals(20L, resumed.trialDaysRemaining(start.plusDays(10)));

        // When 31 days pass, trial is expired and requires startup attestation
        LocalDate afterExpiry = start.plusDays(31);
        LicenseState expired = store.load(afterExpiry);
        assertTrue(expired.isTrialExpired(afterExpiry));
        assertTrue(expired.requiresStartupAttestation(afterExpiry));
        assertTrue(expired.warningMessage().orElse("").startsWith("TrialExpired:"));

        // Attempting to restart the trial after expiry throws IllegalStateException
        assertThrows(IllegalStateException.class, () -> store.startCommercialTrial(afterExpiry));
    }

    @Test
    void environmentVariableOverridesSavedEvaluationModeForZeroTouchEnterpriseDeployments() throws Exception {
        // Given a store where user previously selected Evaluation Mode
        LicenseStore store = newStore();
        LocalDate today = LocalDate.of(2026, 4, 21);
        store.selectEvaluationMode(today);

        // When SMILE_LICENSE_KEY is set in the environment
        String envToken = signToken("""
                {"ver":1,"id":"LIC-2026-0200","customer":"Global Bank","email":"it@bank.com","tier":"ENTERPRISE","seats":100,"issuedAt":"2026-01-01","expiresAt":"2027-01-01"}""");
        store.withExternalTokens(envToken, null);

        // Then load() resolves COMMERCIAL_LICENSED from SMILE_LICENSE_KEY
        LicenseState state = store.load(today);
        assertEquals(UsageMode.COMMERCIAL_LICENSED, state.mode());
        assertEquals("Global Bank", state.key().orElseThrow().customer());
        assertEquals("SMILE_LICENSE_KEY", state.sourceDescription().orElseThrow());
        assertTrue(state.isCommercialEntitled(today));
    }

    @Test
    void isCommercialEntitledReflectsActiveLicenseOrTrialState() throws Exception {
        LicenseStore store = newStore();
        LocalDate start = LocalDate.of(2026, 4, 21);

        // First run (UNSET) -> not commercially entitled
        assertFalse(store.load(start).isCommercialEntitled(start));

        // Evaluation mode -> not commercially entitled
        assertFalse(store.selectEvaluationMode(start).isCommercialEntitled(start));

        // Active 30-day commercial trial -> commercially entitled
        LicenseState trial = store.startCommercialTrial(start);
        assertTrue(trial.isCommercialEntitled(start));
        assertTrue(trial.isCommercialEntitled(start.plusDays(30)));
        assertFalse(store.load(start.plusDays(31)).isCommercialEntitled(start.plusDays(31)));

        // Active commercial license key -> commercially entitled until expiry
        String token = signToken("""
                {"ver":1,"id":"LIC-2026-0300","customer":"Acme Corp","tier":"STUDIO_TEAM","seats":5,"issuedAt":"2026-04-01","expiresAt":"2026-12-31"}""");
        LicenseState licensed = store.activateKey(token, start);
        assertTrue(licensed.isCommercialEntitled(start));
        assertTrue(licensed.isCommercialEntitled(LocalDate.of(2026, 12, 31)));
        assertFalse(store.load(LocalDate.of(2027, 1, 1)).isCommercialEntitled(LocalDate.of(2027, 1, 1)));
    }
}
