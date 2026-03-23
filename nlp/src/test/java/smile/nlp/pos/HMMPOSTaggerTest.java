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
package smile.nlp.pos;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.Bag;

/**
 *
 * @author Haifeng Li
 */
public class HMMPOSTaggerTest {

    public HMMPOSTaggerTest() {

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

    @Test
    public void testWSJ() throws IOException {
        System.out.println("WSJ");

        MathEx.setSeed(19650218); // to get repeatable results.
        List<String[]> sentences = new ArrayList<>();
        List<PennTreebankPOS[]> tags = new ArrayList<>();
        HMMPOSTagger.read("nlp/src/test/resources/data/PennTreebank/PennTreebank2/TAGGED/POS/WSJ", sentences, tags);

        // Data is not available
        if (sentences.isEmpty()) return;

        String[][] x = sentences.toArray(new String[sentences.size()][]);
        PennTreebankPOS[][] y = tags.toArray(new PennTreebankPOS[tags.size()][]);
        
        int n = x.length;
        int k = 10;
        int error = 0;
        int total = 0;

        Bag[] bags = CrossValidation.of(n, k);
        for (int i = 0; i < k; i++) {
            String[][] trainx = MathEx.slice(x, bags[i].samples());
            PennTreebankPOS[][] trainy = MathEx.slice(y, bags[i].samples());
            String[][] testx = MathEx.slice(x, bags[i].oob());
            PennTreebankPOS[][] testy = MathEx.slice(y, bags[i].oob());

            HMMPOSTagger tagger = HMMPOSTagger.fit(trainx, trainy);

            for (int j = 0; j < testx.length; j++) {
                PennTreebankPOS[] label = tagger.tag(testx[j]);
                total += label.length;
                for (int l = 0; l < label.length; l++) {
                    if (label[l] != testy[j][l]) {
                        error++;
                    }
                }
            }
        }

        System.out.format("Error rate = %.2f%% as %d of %d\n", 100.0 * error / total, error, total);
        assertEquals(51325, error, 50);
    }

    @Test
    public void testBrown() throws IOException {
        System.out.println("BROWN");

        MathEx.setSeed(19650218); // to get repeatable results.
        List<String[]> sentences = new ArrayList<>();
        List<PennTreebankPOS[]> tags = new ArrayList<>();
        HMMPOSTagger.read("nlp/src/test/resources/data/PennTreebank/PennTreebank2/TAGGED/POS/BROWN", sentences, tags);

        // Data is not available
        if (sentences.isEmpty()) return;

        String[][] x = sentences.toArray(new String[sentences.size()][]);
        PennTreebankPOS[][] y = tags.toArray(new PennTreebankPOS[tags.size()][]);
        
        int n = x.length;
        int k = 10;
        int error = 0;
        int total = 0;

        Bag[] bags = CrossValidation.of(n, k);
        for (int i = 0; i < k; i++) {
            String[][] trainx = MathEx.slice(x, bags[i].samples());
            PennTreebankPOS[][] trainy = MathEx.slice(y, bags[i].samples());
            String[][] testx = MathEx.slice(x, bags[i].oob());
            PennTreebankPOS[][] testy = MathEx.slice(y, bags[i].oob());

            HMMPOSTagger tagger = HMMPOSTagger.fit(trainx, trainy);

            for (int j = 0; j < testx.length; j++) {
                PennTreebankPOS[] label = tagger.tag(testx[j]);
                total += label.length;
                for (int l = 0; l < label.length; l++) {
                    if (label[l] != testy[j][l]) {
                        error++;
                    }
                }
            }
        }

        System.out.format("Error rate = %.2f%% as %d of %d\n", 100.0 * error / total, error, total);
        assertEquals(55589, error, 50);
    }
}
