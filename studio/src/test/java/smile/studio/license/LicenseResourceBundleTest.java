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

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the localization contract for {@code License}, {@code StatusBar}, and {@code SmileStudio}
 * license strings across the base bundle and all five supported locales.
 *
 * @author Haifeng Li
 */
public class LicenseResourceBundleTest {
    private static final List<Locale> LOCALES = List.of(
            Locale.US,
            Locale.SIMPLIFIED_CHINESE,
            Locale.JAPAN,
            Locale.FRANCE,
            Locale.of("es", "ES"));

    private static final List<String> LICENSE_DIALOG_KEYS = List.of(
            "DialogTitle", "DialogHeader",
            "EvalCardTitle", "EvalCardBody", "ContinueEvalButton",
            "CommercialCardTitle", "CommercialCardBody",
            "LicenseKeyLabel", "LicenseKeyPlaceholder", "ActivateButton",
            "GetLicenseLink", "StartTrialButton",
            "TrialActiveStatus", "TrialExpiredStatus",
            "ActiveLicenseHeader", "ActiveCustomer", "ActiveTierSeats",
            "ActiveExpires", "ActiveSource", "DeactivateButton",
            "CancelButton", "FooterTerms",
            "ErrorEmptyKey", "ErrorMalformedKey", "ErrorInvalidSignature",
            "ErrorExpiredKey", "ErrorInvalidPayload");

    private static final List<String> STATUS_BAR_KEYS = List.of(
            "Ready", "SystemInfo",
            "BadgeEvaluation", "BadgeTrial", "BadgeLicensed",
            "TooltipEvaluation", "TooltipTrial", "TooltipLicensed");

    private static final List<String> STUDIO_LICENSE_KEYS = List.of(
            "License", "About",
            "TitleEvaluation", "TitleTrial", "TitleLicensed",
            "AboutStatusEvaluation", "AboutStatusTrial", "AboutStatusLicensed",
            "AboutMessage");

    @Test
    public void testBaseBundlesContainAllLicenseKeys() {
        assertBundleKeys(LicenseDialog.BUNDLE_NAME, Locale.ROOT, LICENSE_DIALOG_KEYS);
        assertBundleKeys("smile.studio.StatusBar", Locale.ROOT, STATUS_BAR_KEYS);
        assertBundleKeys("smile.studio.SmileStudio", Locale.ROOT, STUDIO_LICENSE_KEYS);
    }

    @Test
    public void testEveryLocaleContainsAllLicenseKeys() {
        for (Locale locale : LOCALES) {
            assertBundleKeys(LicenseDialog.BUNDLE_NAME, locale, LICENSE_DIALOG_KEYS);
            assertBundleKeys("smile.studio.StatusBar", locale, STATUS_BAR_KEYS);
            assertBundleKeys("smile.studio.SmileStudio", locale, STUDIO_LICENSE_KEYS);
        }
    }

    private static void assertBundleKeys(String baseName, Locale locale, List<String> keys) {
        ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale);
        for (String key : keys) {
            assertTrue(bundle.containsKey(key),
                    baseName + " (" + locale + ") is missing key: " + key);
            assertFalse(bundle.getString(key).isBlank(),
                    baseName + " (" + locale + ") has a blank value for key: " + key);
        }
    }
}
