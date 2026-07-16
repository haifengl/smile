/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.nlp.tokenizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EnglishAbbreviations}.
 *
 * @author Haifeng Li
 */
public class EnglishAbbreviationsTest {

    @Test
    public void testContainsCommonAbbreviations() {
        // Given the English abbreviations dictionary
        // When checking well-known abbreviations (stored lowercase)
        // Then they should be found
        assertTrue(EnglishAbbreviations.contains("mr"),   "Expected 'mr' in abbreviations");
        assertTrue(EnglishAbbreviations.contains("mrs"),  "Expected 'mrs' in abbreviations");
        assertTrue(EnglishAbbreviations.contains("dr"),   "Expected 'dr' in abbreviations");
        assertTrue(EnglishAbbreviations.contains("jr"),   "Expected 'jr' in abbreviations");
    }

    @Test
    public void testDoesNotContainRegularWord() {
        // Given the English abbreviations dictionary
        // When checking a plain word that is not an abbreviation
        // Then it should not be found
        assertFalse(EnglishAbbreviations.contains("hello"));
        assertFalse(EnglishAbbreviations.contains("world"));
        assertFalse(EnglishAbbreviations.contains(""));
    }

    @Test
    public void testSize() {
        // Given the English abbreviations dictionary
        // When querying the number of abbreviations
        // Then the count should be a reasonable positive number
        int size = EnglishAbbreviations.size();
        assertTrue(size > 10,  "Expected more than 10 abbreviations, got " + size);
        assertTrue(size < 5000, "Expected fewer than 5000 abbreviations, got " + size);
    }

    @Test
    public void testIterator() {
        // Given the English abbreviations dictionary
        // When iterating over all entries
        // Then all entries are non-null and count matches size()
        int count = 0;
        var it = EnglishAbbreviations.iterator();
        while (it.hasNext()) {
            String abbr = it.next();
            assertNotNull(abbr, "Expected non-null abbreviation");
            assertFalse(abbr.isEmpty(), "Expected non-empty abbreviation");
            count++;
        }
        assertEquals(EnglishAbbreviations.size(), count);
    }
}

