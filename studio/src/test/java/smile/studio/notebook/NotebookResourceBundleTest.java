/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
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
        assertTrue(bundle.containsKey(UNSUPPORTED_KERNEL_KEY),
                "base bundle is missing key: " + UNSUPPORTED_KERNEL_KEY);
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
            assertTrue(bundle.containsKey(UNSUPPORTED_KERNEL_KEY),
                    locale + " bundle is missing key: " + UNSUPPORTED_KERNEL_KEY);
        }
    }
}
