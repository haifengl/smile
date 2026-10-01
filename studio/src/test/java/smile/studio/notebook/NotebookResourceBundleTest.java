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
package smile.studio.notebook;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the notebook execution messages.
 *
 * <p>Running a cell while its kernel is still starting up used to show the
 * unsupported-language dialog ("The kernel for Scala is not supported yet."),
 * because a null kernel was the only signal the notebook had and it could not
 * tell "not ready yet" from "never supported". These tests pin the distinct
 * starting-up message and the keys every locale must define.
 *
 * @author Haifeng Li
 */
public class NotebookResourceBundleTest {
    /** Every locale that ships a Notebook bundle. */
    private static final List<Locale> LOCALES = List.of(
            Locale.US,
            Locale.SIMPLIFIED_CHINESE,
            Locale.JAPAN,
            Locale.FRANCE,
            Locale.of("es", "ES"));

    /**
     * Keys the notebook reads when the kernel is starting up. Each takes the
     * language name as its {@link java.text.MessageFormat} argument.
     */
    private static final List<String> STARTUP_KEYS = List.of(
            "KernelStartingTitle", "KernelStartingMessage", "KernelInitErrorMessage");

    /**
     * Keys the notebook reads to display status bar messages during kernel lifecycle.
     */
    private static final List<String> STATUS_KEYS = List.of(
            "KernelStarting", "KernelReady");

    /** The key that must survive for genuinely unsupported languages. */
    private static final String UNSUPPORTED_KERNEL_KEY = "UnsupportedKernelMessage";

    @Test
    public void testBaseBundleHasStartupKeys() {
        System.out.println("Notebook: base bundle defines the kernel startup keys");
        ResourceBundle bundle = ResourceBundle.getBundle(Notebook.class.getName(), Locale.ROOT);
        for (String key : STARTUP_KEYS) {
            assertTrue(bundle.containsKey(key),
                    "base bundle is missing key: " + key);
        }
        for (String key : STATUS_KEYS) {
            assertTrue(bundle.containsKey(key),
                    "base bundle is missing status key: " + key);
        }
        assertTrue(bundle.containsKey(UNSUPPORTED_KERNEL_KEY),
                "base bundle is missing key: " + UNSUPPORTED_KERNEL_KEY);

        assertEquals("Kernel is starting...", bundle.getString("KernelStarting"));
        assertEquals("Kernel is ready", bundle.getString("KernelReady"));
    }

    @Test
    public void testEveryLocaleHasStartupKeys() {
        System.out.println("Notebook: every locale defines the kernel startup keys");
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(Notebook.class.getName(), locale);
            for (String key : STARTUP_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " bundle has a blank value for key: " + key);
            }
            for (String key : STATUS_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " bundle is missing status key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " bundle has a blank value for status key: " + key);
            }
            assertTrue(bundle.containsKey(UNSUPPORTED_KERNEL_KEY),
                    locale + " bundle is missing key: " + UNSUPPORTED_KERNEL_KEY);
        }
    }
}
