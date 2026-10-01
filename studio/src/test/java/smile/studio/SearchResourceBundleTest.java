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

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.fife.rsta.ui.search.SearchListener;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import smile.studio.kernel.ScalaKernel;
import smile.studio.workspace.OpenFile;

/**
 * Regression tests for the Find and Replace menu.
 *
 * <p>A missing resource key used to make {@code SmileStudio.searchEvent} throw
 * {@link java.util.MissingResourceException} on the event dispatch thread, so
 * Find and Replace silently did nothing when a plain text file was selected.
 * These tests pin the keys and the routing contract that fixed it.
 *
 * @author Haifeng Li
 */
public class SearchResourceBundleTest {
    /** Every locale that ships a SmileStudio bundle. */
    private static final List<Locale> LOCALES = List.of(
            Locale.US,
            Locale.SIMPLIFIED_CHINESE,
            Locale.JAPAN,
            Locale.FRANCE,
            Locale.of("es", "ES"));

    /** Keys the Find and Replace menu reads at runtime. */
    private static final List<String> REQUIRED_KEYS = List.of(
            "FindMenu", "Search", "NoActiveFile", "Find", "Replace");

    /** Keys the Scala kernel reads when it fails to start scala-cli. */
    private static final List<String> SCALA_KERNEL_KEYS = List.of(
            "ScalaCliInstallTitle", "ScalaCliInstallMessage");

    @Test
    public void testBaseBundleHasSearchKeys() {
        System.out.println("SmileStudio: base bundle defines the search keys");
        ResourceBundle bundle = ResourceBundle.getBundle(SmileStudio.class.getName(), Locale.ROOT);
        for (String key : REQUIRED_KEYS) {
            assertTrue(bundle.containsKey(key),
                    "base bundle is missing key: " + key);
        }
    }

    @Test
    public void testEveryLocaleHasSearchKeys() {
        System.out.println("SmileStudio: every locale defines the search keys");
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(SmileStudio.class.getName(), locale);
            for (String key : REQUIRED_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " bundle has a blank value for key: " + key);
            }
        }
    }

    @Test
    public void testOpenFileIsASearchListener() {
        System.out.println("OpenFile: is a SearchListener so the Find menu can route to any tab");
        assertTrue(SearchListener.class.isAssignableFrom(OpenFile.class),
                "OpenFile must extend SearchListener for Find/Replace to reach a text tab");
    }

    @Test
    public void testScalaKernelBundleHasEveryLocale() {
        System.out.println("ScalaKernel: every locale defines the scala-cli install hint");
        // A key added to the base bundle but not to a locale is a
        // MissingResourceException at runtime.
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(ScalaKernel.class.getName(), locale);
            for (String key : SCALA_KERNEL_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " ScalaKernel bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " ScalaKernel bundle has a blank value for key: " + key);
            }
        }
    }
}
