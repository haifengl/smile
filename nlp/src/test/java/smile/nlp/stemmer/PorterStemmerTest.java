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
package smile.nlp.stemmer;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class PorterStemmerTest {

    public PorterStemmerTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of stem method, of class PorterStemmer.
     */
    @Test
    public void testStem() {
        System.out.println("stem");
        String[] words = {"consign", "consigned", "consigning", "consignment",
            "consist", "consisted", "consistency", "consistent", "consistently",
            "consisting", "consists", "consolation", "consolations", "consolatory",
            "console", "consoled", "consoles", "consolidate", "consolidated",
            "consolidating", "consoling", "consolingly", "consols", "consonant",
            "consort", "consorted", "consorting", "conspicuous", "conspicuously",
            "conspiracy", "conspirator", "conspirators", "conspire", "conspired",
            "conspiring", "constable", "constables", "constance", "constancy",
            "constant", "knack", "knackeries", "knacks", "knag", "knave",
            "knaves", "knavish", "kneaded", "kneading", "knee", "kneel",
            "kneeled", "kneeling", "kneels", "knees", "knell", "knelt", "knew",
            "knick", "knif", "knife", "knight", "knightly", "knights", "knit",
            "knits", "knitted", "knitting", "knives", "knob", "knobs", "knock",
            "knocked", "knocker", "knockers", "knocking", "knocks", "knopp",
            "knot", "knots"
        };

        String[] expResult = {"consign", "consign", "consign", "consign",
            "consist", "consist", "consist", "consist", "consist", "consist",
            "consist", "consol", "consol", "consolatori", "consol", "consol",
            "consol", "consolid", "consolid", "consolid", "consol", "consolingli",
            "consol", "conson", "consort", "consort", "consort", "conspicu",
            "conspicu", "conspiraci", "conspir", "conspir", "conspir", "conspir",
            "conspir", "constabl", "constabl", "constanc", "constanc", "constant",
            "knack", "knackeri", "knack", "knag", "knave", "knave", "knavish",
            "knead", "knead", "knee", "kneel", "kneel", "kneel", "kneel", "knee",
            "knell", "knelt", "knew", "knick", "knif", "knife", "knight",
            "knightli", "knight", "knit", "knit", "knit", "knit", "knive", "knob",
            "knob", "knock", "knock", "knocker", "knocker", "knock", "knock",
            "knopp", "knot", "knot"
        };

        PorterStemmer instance = new PorterStemmer();
        for (int i = 0; i < words.length; i++) {
            String result = instance.stem(words[i]);
            assertEquals(expResult[i], result);
        }
    }

    /**
     * Test that words of 1–2 characters are returned unchanged.
     */
    @Test
    public void testStemShortWords() {
        // Given a PorterStemmer
        // When stemming words of length ≤ 2
        // Then they are returned unchanged
        PorterStemmer instance = new PorterStemmer();
        assertEquals("a",  instance.stem("a"));
        assertEquals("it", instance.stem("it"));
        assertEquals("be", instance.stem("be"));
    }

    /**
     * Test that an empty string is returned unchanged.
     */
    @Test
    public void testStemEmptyString() {
        // Given a PorterStemmer
        // When stemming an empty string
        // Then the empty string is returned
        PorterStemmer instance = new PorterStemmer();
        assertEquals("", instance.stem(""));
    }

    /**
     * Test that null input is returned as null.
     */
    @Test
    public void testStemNull() {
        // Given a PorterStemmer
        // When stemming null
        // Then null is returned
        PorterStemmer instance = new PorterStemmer();
        assertNull(instance.stem(null));
    }

    /**
     * Test the apply() method from the Stemmer / Function interface.
     */
    @Test
    public void testApplyFunctionInterface() {
        // Given a Stemmer used as Function<String, String>
        // When calling apply()
        // Then it produces the same result as stem()
        Stemmer instance = new PorterStemmer();
        assertEquals(instance.stem("running"), instance.apply("running"));
        assertEquals(instance.stem("caresses"), instance.apply("caresses"));
    }

    /**
     * Test stripPluralParticiple on standard plural and -ing forms.
     */
    @Test
    public void testStripPluralParticiple() {
        // Given a PorterStemmer
        // When calling stripPluralParticiple on typical plural/participle words
        // Then plurals and -ing endings are removed
        PorterStemmer instance = new PorterStemmer();
        assertEquals("cat",   instance.stripPluralParticiple("cats"));
        assertEquals("run",   instance.stripPluralParticiple("running"));
        assertEquals("caress", instance.stripPluralParticiple("caresses"));
    }

    /**
     * Test that stripPluralParticiple preserves special short words.
     */
    @Test
    public void testStripPluralParticiplePreservesSpecialWords() {
        // Given a PorterStemmer
        // When calling stripPluralParticiple on preserved words
        // Then they are returned unchanged
        PorterStemmer instance = new PorterStemmer();
        assertEquals("was",  instance.stripPluralParticiple("was"));
        assertEquals("has",  instance.stripPluralParticiple("has"));
        assertEquals("his",  instance.stripPluralParticiple("his"));
        assertEquals("this", instance.stripPluralParticiple("this"));
    }

    /**
     * Test that stripPluralParticiple handles null and empty input safely.
     */
    @Test
    public void testStripPluralParticipleEdgeCases() {
        // Given a PorterStemmer
        // When calling stripPluralParticiple with empty string or null
        // Then they are returned unchanged
        PorterStemmer instance = new PorterStemmer();
        assertEquals("", instance.stripPluralParticiple(""));
        assertNull(instance.stripPluralParticiple(null));
    }

    /**
     * Test that common inflected forms all reduce to the same stem.
     */
    @Test
    public void testStemGroupsToSameStem() {
        // Given a PorterStemmer
        // When stemming different inflections of the same word
        // Then all produce the same stem
        PorterStemmer instance = new PorterStemmer();
        String[] running = {"run", "running", "runner"};
        // "run" (2 chars) passes through unchanged; "running" → "run"; "runner" → "runner"
        // Focus on longer forms that should share a stem
        assertEquals(instance.stem("agreed"),   instance.stem("agreeing"));
        assertEquals(instance.stem("matting"),  instance.stem("matted"));
        assertEquals(instance.stem("disabled"), instance.stem("disabling"));
    }
}
