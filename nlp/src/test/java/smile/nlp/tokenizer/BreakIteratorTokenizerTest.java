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
public class BreakIteratorTokenizerTest {

    public BreakIteratorTokenizerTest() {
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
     * Test of split method, of class BreakIteratorWordTokenizer.
     */
    @Test
    public void testSplit() {
        System.out.println("tokenize");
        String text = "Good muffins cost $3.88\nin New York.  Please buy "
                + "me\ntwo of them.\n\nYou cannot eat them. I gonna eat them. "
                + "Thanks. Of course, I won't. ";

        String[] expResult = {"Good", "muffins", "cost", "$3.88", "in",
            "New", "York", ".", "Please", "buy", "me", "two", "of", "them", ".",
            "You", "cannot", "eat", "them", ".", "I", "gonna", "eat", "them", ".",
            "Thanks", ".", "Of", "course", ",", "I", "won't", "."};

        BreakIteratorTokenizer instance = new BreakIteratorTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class BreakIteratorWordTokenizer.
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
            "contractions", ":", "'", "tis", ",", "'", "twas", ",", "ain't",
            ",", "aren't", ",", "Can't", ",", "could've",
            ",", "couldn't", ",", "didn't", ",", "doesn't", ",", "don't",
            ",", "hasn't", ",", "he'd", ",", "he'll", ",",
            "he's", ",", "how'd", ",", "how'll", ",", "how's",
            ",", "i'd", ",", "i'll", ",", "i'm", ",", "i've", ",",
            "isn't", ",", "it's", ",", "might've", ",", "mightn't",
            ",", "must've", ",", "mustn't", ",", "Shan't",
            ",", "she'd", ",", "she'll", ",", "she's", ",",
            "should've", ",", "shouldn't", ",", "that'll", ",",
            "that's", ",", "there's", ",", "they'd", ",", "they'll",
            ",", "they're", ",", "they've", ",", "wasn't", ",", "we'd",
            ",", "we'll", ",", "we're", ",", "weren't", ",",
            "what'd", ",", "what's", ",", "when'd",
            ",", "when'll", ",", "when's", ",", "where'd", ",",
            "where'll", ",", "where's", ",", "who'd", ",", "who'll",
            ",", "who's", ",", "why'd", ",", "why'll", ",",
            "why's", ",", "Won't", ",", "would've", ",", "wouldn't",
            ",", "you'd", ",", "you'll", ",", "you're", ",",
            "you've"};

        BreakIteratorTokenizer instance = new BreakIteratorTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class BreakIteratorWordTokenizer.
     */
    @Test
    public void testSplitAbbreviation() {
        System.out.println("tokenize");
        String text = "Here are some examples of abbreviations: A.B., abbr., "
                + "acad., A.D., alt., A.M., B.C., etc.";

        String[] expResult = {"Here", "are", "some", "examples", "of",
            "abbreviations", ":", "A.B", ".", ",", "abbr", ".", ",", "acad", ".",
            ",", "A.D", ".", ",", "alt", ".", ",", "A.M", ".", ",",
            "B.C", ".", ",", "etc", "."};

        BreakIteratorTokenizer instance = new BreakIteratorTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class BreakIteratorWordTokenizer.
     */
    @Test
    public void testSplitTis() {
        System.out.println("tokenize tis");
        String text = "'tis, 'tisn't, and 'twas were common in early modern English texts.";
        String[] expResult = {"'", "tis", ",", "'", "tisn't", ",", "and",
            "'", "twas", "were", "common", "in", "early", "modern", "English",
            "texts", "."};

        BreakIteratorTokenizer instance = new BreakIteratorTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class BreakIteratorWordTokenizer.
     */
    @Test
    public void testSplitHyphen() {
        System.out.println("tokenize hyphen");
        String text = "On a noncash basis for the quarter, the bank reported a "
                + "loss of $7.3 billion because of a $10.4 billion write-down "
                + "in the value of its credit card unit, attributed to federal "
                + "regulations that limit debit fees and other charges.";

        String[] expResult = {"On", "a", "noncash", "basis", "for", "the",
            "quarter", ",", "the", "bank", "reported", "a", "loss", "of",
            "$7.3", "billion", "because", "of", "a", "$10.4", "billion",
            "write-down", "in", "the", "value", "of", "its", "credit", "card",
            "unit", ",", "attributed", "to", "federal", "regulations", "that",
            "limit", "debit", "fees", "and", "other", "charges", "."};

        BreakIteratorTokenizer instance = new BreakIteratorTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class BreakIteratorWordTokenizer.
     */
    @Test
    public void testSplitSingleQuote() {
        System.out.println("tokenize single quote");
        String text = "String literals can be enclosed in matching single "
                + "quotes ('). But it's also appearing in contractions such as can't.";

        String[] expResult = {"String", "literals", "can", "be", "enclosed", "in",
            "matching", "single", "quotes", "(", "'", ")", ".", "But", "it's", "also",
            "appearing", "in", "contractions", "such", "as", "can't", "."};

        BreakIteratorTokenizer instance = new BreakIteratorTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }

    /**
     * Test of split method, of class BreakIteratorWordTokenizer.
     */
    @Test
    public void testSplitRomanNumeral() {
        System.out.println("tokenize roman numeral");
        String text = "S.. or S: means \"twice\" (as in \"twice a third\").";
        String[] expResult = {"S", ".", ".", "or", "S", ":", "means", "\"", "twice",
            "\"", "(", "as", "in", "\"", "twice", "a", "third", "\"", ")", "."};

        BreakIteratorTokenizer instance = new BreakIteratorTokenizer();
        String[] result = instance.split(text);

        assertEquals(expResult.length, result.length);
        for (int i = 0; i < result.length; i++) {
            assertEquals(expResult[i], result[i]);
        }
    }
}
