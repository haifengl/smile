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
package smile.studio.plugin;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the plugin resource bundles. Studio ships five locales; a key added to the
 * base bundle but missing from a localized one is a {@link java.util.MissingResourceException}
 * at runtime, so every plugin key must exist in all five.
 *
 * @author Haifeng Li
 */
public class PluginResourceBundleTest {
    /** Every locale that ships a Studio bundle. */
    private static final List<Locale> LOCALES = List.of(
            Locale.US,
            Locale.SIMPLIFIED_CHINESE,
            Locale.JAPAN,
            Locale.FRANCE,
            Locale.of("es", "ES"));

    /** Keys the plugin panel reads. */
    private static final List<String> KEYS = List.of(
            "Plugins", "Discover", "Installed", "Marketplaces", "Errors",
            "Install", "Uninstall", "Enable", "Disable", "AddMarketplace",
            "MCP", "RestartRequired",
            "Close", "Remove", "Source", "MCPServers");

    @Test
    public void testBaseBundleHasPluginKeys() {
        System.out.println("Plugin: base bundle defines the plugin keys");
        ResourceBundle bundle = ResourceBundle.getBundle(
                "smile.studio.plugin.Plugin", Locale.ROOT);
        for (String key : KEYS) {
            assertTrue(bundle.containsKey(key), "base bundle is missing key: " + key);
        }
    }

    @Test
    public void testEveryLocaleHasPluginKeys() {
        System.out.println("Plugin: every locale defines the plugin keys");
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle("smile.studio.plugin.Plugin", locale);
            for (String key : KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " bundle has a blank value for key: " + key);
            }
        }
    }
}
