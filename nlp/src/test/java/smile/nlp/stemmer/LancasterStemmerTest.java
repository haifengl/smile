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
 * @author Haifeng
 */
public class LancasterStemmerTest {

    public LancasterStemmerTest() {
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
     * Test of stem method, of class LancasterStemmer.
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
            "consist", "consol", "consol", "consol", "consol", "consol",
            "consol", "consolid", "consolid", "consolid", "consol", "consol",
            "consol", "conson", "consort", "consort", "consort", "conspicu",
            "conspicu", "conspir", "conspir", "conspir", "conspir", "conspir",
            "conspir", "const", "const", "const", "const", "const", "knack",
            "knackery", "knack", "knag", "knav", "knav", "knav", "knead",
            "knead", "kne", "kneel", "kneel", "kneel", "kneel", "kne", "knel",
            "knelt", "knew", "knick", "knif", "knif", "knight", "knight",
            "knight", "knit", "knit", "knit", "knit", "kniv", "knob", "knob",
            "knock", "knock", "knock", "knock", "knock", "knock", "knop",
            "knot", "knot"
        };

        LancasterStemmer instance = new LancasterStemmer();
        for (int i = 0; i < words.length; i++) {
            String result = instance.stem(words[i]);
            assertEquals(expResult[i], result);
        }
    }

    /**
     * Test that words of length ≤ 3 are returned unchanged.
     */
    @Test
    public void testStemShortWords() {
        // Given a LancasterStemmer
        // When stemming words of length ≤ 3
        // Then they are returned unchanged (cleanup converts to lower but no suffix stripping)
        LancasterStemmer instance = new LancasterStemmer();
        assertEquals("a",   instance.stem("a"));
        assertEquals("it",  instance.stem("it"));
        assertEquals("the", instance.stem("the"));
    }

    /**
     * Test that an empty string is returned unchanged.
     */
    @Test
    public void testStemEmptyString() {
        // Given a LancasterStemmer
        // When stemming an empty string
        // Then the empty string is returned
        LancasterStemmer instance = new LancasterStemmer();
        assertEquals("", instance.stem(""));
    }

    /**
     * Test that null input is returned as null.
     */
    @Test
    public void testStemNull() {
        // Given a LancasterStemmer
        // When stemming null
        // Then null is returned
        LancasterStemmer instance = new LancasterStemmer();
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
        Stemmer instance = new LancasterStemmer();
        assertEquals(instance.stem("running"),   instance.apply("running"));
        assertEquals(instance.stem("consoling"), instance.apply("consoling"));
    }

    /**
     * Test that non-letter characters are stripped before stemming.
     */
    @Test
    public void testStemStripsNonLetters() {
        // Given a LancasterStemmer
        // When stemming a word containing digits or punctuation
        // Then non-letter characters are removed before stemming
        LancasterStemmer instance = new LancasterStemmer();
        // "consist123" → cleanup → "consist" → stem
        assertEquals(instance.stem("consist"), instance.stem("consist123"));
        assertEquals(instance.stem("consign"), instance.stem("consign!!"));
    }

    /**
     * Test uppercase input is lowercased before stemming.
     */
    @Test
    public void testStemUppercaseInput() {
        // Given a LancasterStemmer
        // When stemming an uppercase word
        // Then it produces the same result as the lowercase form
        LancasterStemmer instance = new LancasterStemmer();
        assertEquals(instance.stem("consign"), instance.stem("CONSIGN"));
        assertEquals(instance.stem("consisting"), instance.stem("Consisting"));
    }

    /**
     * Test that the stripPrefix=true constructor strips known prefixes.
     */
    @Test
    public void testStemWithStripPrefix() {
        // Given a LancasterStemmer with prefix stripping enabled
        // When stemming words that start with known prefixes
        // Then the prefix is stripped before suffix stemming
        LancasterStemmer withPrefix    = new LancasterStemmer(true);
        LancasterStemmer withoutPrefix = new LancasterStemmer(false);

        // "kilogram" → strip "kilo" → "gram" → stem
        String withPrefixResult    = withPrefix.stem("kilogram");
        String withoutPrefixResult = withoutPrefix.stem("kilogram");
        // The stems will differ because prefix stripping changes the input
        assertNotEquals(withPrefixResult, withoutPrefixResult,
                "stripPrefix=true should produce a different stem for 'kilogram'");

        // "microphone" → strip "micro" → "phone" → stem
        String microWith    = withPrefix.stem("microphone");
        String microWithout = withoutPrefix.stem("microphone");
        assertNotEquals(microWith, microWithout,
                "stripPrefix=true should produce a different stem for 'microphone'");
    }

    /**
     * Test that inflected forms of the same word share the same Lancaster stem.
     */
    @Test
    public void testStemGroupsToSameStem() {
        // Given a LancasterStemmer
        // When stemming different inflections of "consist"
        // Then all reduce to the same stem
        LancasterStemmer instance = new LancasterStemmer();
        String stem = instance.stem("consist");
        assertEquals(stem, instance.stem("consisted"));
        assertEquals(stem, instance.stem("consistency"));
        assertEquals(stem, instance.stem("consisting"));
        assertEquals(stem, instance.stem("consists"));
    }
}