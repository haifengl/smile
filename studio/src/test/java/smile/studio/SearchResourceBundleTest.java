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
package smile.studio;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.fife.rsta.ui.search.SearchListener;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
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
            new Locale("es", "ES"));

    /** Keys the Find and Replace menu reads at runtime. */
    private static final List<String> REQUIRED_KEYS = List.of(
            "FindMenu", "Search", "NoActiveFile", "Find", "Replace");

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
}
