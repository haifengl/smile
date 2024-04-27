/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.nlp.tokenizer;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class SimpleTokenizerTest {

    public SimpleTokenizerTest() {
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
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testTokenize() {
        System.out.println("tokenize");
        String text = "Good muffins cost $3.88\nin New York.  Please buy "
                + "me\ntwo of them.\n\nYou cannot eat them. I gonna eat them. "
                + "Thanks. Of course, I won't. ";

        String[] expResult = {"Good", "muffins", "cost", "$", "3.88", "in",
            "New", "York.", "Please", "buy", "me", "two", "of", "them", ".",
            "You", "can", "not", "eat", "them.", "I", "gon", "na", "eat",
            "them.", "Thanks.", "Of", "course", ",", "I", "will", "not", "."};

        SimpleTokenizer instance = new SimpleTokenizer(true);
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testSplitContraction() {
        System.out.println("tokenize contraction");
        String text = "Here are some examples of contractions: 'tis, "
                + "'twas, ain't, aren't, Can't, could've, couldn't, didn't, doesn't, "
                + "don't, hasn't, he'd, he'll, he's, how'd, how'll, how's, i'd, i'll, i'm, "
                + "i've, isn't, it's, might've, mightn't, must've, mustn't, Shan't, "
                + "she'd, she'll, she's, should've, shouldn't, that'll, that's, "
                + "there's, they'd, they'll, they're, they've, wasn't, we'd, we'll, "
                + "we're, weren't, what'd, what's, when'd, when'll, when's, "
                + "where'd, where'll, where's, who'd, who'll, who's, why'd, why'll, "
                + "why's, Won't, would've, wouldn't, you'd, you'll, you're, you've";

        String[] expResult = {"Here", "are", "some", "examples", "of",
            "contractions", ":", "'t", "is", ",", "'t", "was", ",", "am",
            "not", ",", "are", "not", ",", "Can", "not", ",", "could", "'ve",
            ",", "could", "not", ",", "did", "not", ",", "does", "not", ",", "do",
            "not", ",", "has", "not", ",", "he", "'d", ",", "he", "'ll", ",",
            "he", "'s", ",", "how", "'d", ",", "how", "'ll", ",", "how", "'s",
            ",", "i", "'d", ",", "i", "'ll", ",", "i", "'m", ",", "i", "'ve", ",",
            "is", "not", ",", "it", "'s", ",", "might", "'ve", ",", "might",
            "not", ",", "must", "'ve", ",", "must", "not", ",", "Shall",
            "not", ",", "she", "'d", ",", "she", "'ll", ",", "she", "'s", ",",
            "should", "'ve", ",", "should", "not", ",", "that", "'ll", ",",
            "that", "'s", ",", "there", "'s", ",", "they", "'d", ",", "they", "'ll",
            ",", "they", "'re", ",", "they", "'ve", ",", "was", "not", ",", "we",
            "'d", ",", "we", "'ll", ",", "we", "'re", ",", "were", "not", ",",
            "what", "'d", ",", "what", "'s", ",", "when",
            "'d", ",", "when", "'ll", ",", "when", "'s", ",", "where", "'d", ",",
            "where", "'ll", ",", "where", "'s", ",", "who", "'d", ",", "who",
            "'ll", ",", "who", "'s", ",", "why", "'d", ",", "why", "'ll", ",",
            "why", "'s", ",", "Will", "not", ",", "would", "'ve", ",", "would",
            "not", ",", "you", "'d", ",", "you", "'ll", ",", "you", "'re", ",",
            "you", "'ve"};

        SimpleTokenizer instance = new SimpleTokenizer(true);
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testSplitAbbreviation() {
        System.out.println("tokenize abbreviation");
        String text = "Here are some examples of abbreviations: A.B., abbr., "
                + "acad., A.D., alt., A.M., B.C., etc.";

        String[] expResult = {"Here", "are", "some", "examples", "of",
            "abbreviations", ":", "A.B.", ",", "abbr.", ",", "acad.",
            ",", "A.D.", ",", "alt.", ",", "A.M.", ",", "B.C.", ",", "etc.", "."};

        SimpleTokenizer instance = new SimpleTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testTokenizeTis() {
        System.out.println("tokenize tis");
        String text = "'tis, 'tisn't, and 'twas were common in early modern English texts.";
        String[] expResult = {"'t", "is", ",", "'t", "is", "not", ",", "and",
            "'t", "was", "were", "common", "in", "early", "modern", "English",
            "texts", "."};

        SimpleTokenizer instance = new SimpleTokenizer(true);
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testTokenizeHyphen() {
        System.out.println("tokenize hyphen");
        String text = "On a noncash basis for the quarter, the bank reported a "
                + "loss of $7.3 billion because of a $10.4 billion write-down "
                + "in the value of its credit card unit, attributed to federal "
                + "regulations that limit debit fees and other charges.";

        String[] expResult = {"On", "a", "noncash", "basis", "for", "the",
            "quarter", ",", "the", "bank", "reported", "a", "loss", "of", "$",
            "7.3", "billion", "because", "of", "a", "$", "10.4", "billion",
            "write-down", "in", "the", "value", "of", "its", "credit", "card",
            "unit", ",", "attributed", "to", "federal", "regulations", "that",
            "limit", "debit", "fees", "and", "other", "charges", "."};

        SimpleTokenizer instance = new SimpleTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testTokenizeSingleQuote() {
        System.out.println("tokenize single quote");
        String text = "String literals can be enclosed in matching single "
                + "quotes ('). But it's also appearing in contractions such as can't.";

        String[] expResult = {"String", "literals", "can", "be", "enclosed", "in",
            "matching", "single", "quotes", "(", "'", ")", ".", "But", "it", "'s", "also",
            "appearing", "in", "contractions", "such", "as", "can", "not", "."};

        SimpleTokenizer instance = new SimpleTokenizer(true);
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testTokenizeRomanNumeral() {
        System.out.println("tokenize roman numeral");
        String text = "S.. or S: means \"twice\" (as in \"twice a third\").";
        String[] expResult = {"S..", "or", "S", ":", "means", "\"", "twice",
            "\"", "(", "as", "in", "\"", "twice", "a", "third", "\"", ")", "."};

        SimpleTokenizer instance = new SimpleTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testTokenizeMixedAlphanumWords() {
        System.out.println("tokenize words with mixed numbers, letters, and punctuation");
        String text = "3M, L-3, BB&T, AutoZone, O'Reilly, Harley-Davidson, CH2M, A-Mark, "
                + "Quad/Graphics, Bloomin' Brands, B/E Aerospace, J.Crew, E*Trade.";

        // Note: would be very hard to get "Bloomin'" and "E*Trade" correct
        String[] expResult = {"3M", ",", "L-3", ",", "BB&T", ",", "AutoZone", ",", "O'Reilly",
                ",", "Harley-Davidson", ",", "CH2M", ",", "A-Mark", ",", "Quad/Graphics", ",", "Bloomin",
                "'", "Brands", ",", "B/E", "Aerospace", ",", "J.Crew", ",", "E", "*", "Trade", "."};

        SimpleTokenizer instance = new SimpleTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class SimpleTokenizer.
     */
    @Test
    public void testTokenizeDiacritizedWords() {
        System.out.println("tokenize words with diacritized chars (both composite and combining)");
        String text = "The naïve résumé of Raúl Ibáñez; re\u0301sume\u0301.";
        String[] expResult = {"The", "naïve", "résumé", "of", "Raúl", "Ibáñez", ";", "re\u0301sume\u0301", "."};

        SimpleTokenizer instance = new SimpleTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class TreebankWordTokenizer.
     */
    @Test
    public void testTokenizeNonLatinChars() {
        System.out.println("tokenize words containing non-Latin chars");
        // See https://en.wikipedia.org/wiki/Zero-width_non-joiner
        String text = "می‌خواهم   עֲו‌ֹנֹת   Auf‌lage";
        String[] expResult = {"می‌خواهم", "עֲו‌ֹנֹת", "Auf‌lage"};

        SimpleTokenizer instance = new SimpleTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class TreebankWordTokenizer.
     */
    @Test
    public void testTokenizeVariousSpaces() {
        System.out.println("tokenize words separated by various kinds of space");
        // No-break space and em-space
        String text = "the\u00A0cat\u2003the_cat";
        String[] expResult = {"the", "cat", "the_cat"};

        SimpleTokenizer instance = new SimpleTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class TreebankWordTokenizer.
     */
    @Test
    public void testTokenizeToC() {
        System.out.println("tokenize 1.2 Interpretation.....................................................................................................................3");
        // No-break space and em-space
        String text = "1.2 Interpretation.....................................................................................................................3";
        String[] expResult = {"1.2", "Interpretation", ".....................................................................................................................", "3"};

        SimpleTokenizer instance = new SimpleTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }
}
