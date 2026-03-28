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
}
