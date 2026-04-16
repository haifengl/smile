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

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.Bag;

/**
 * Tests for {@link HMMPOSTagger}.
 *
 * @author Haifeng Li
 */
public class HMMPOSTaggerTest {

    // -----------------------------------------------------------------------
    // Default pre-trained tagger
    // -----------------------------------------------------------------------

    @Test
    public void testDefaultTaggerIsAvailable() {
        // Given / When
        HMMPOSTagger tagger = HMMPOSTagger.getDefault();
        // Then: model resource is bundled and must load successfully
        assertNotNull(tagger, "Default HMM POS tagger should load from bundled resource");
    }

    @Test
    public void testDefaultTaggerTagsSimpleSentence() {
        // Given
        HMMPOSTagger tagger = HMMPOSTagger.getDefault();
        if (tagger == null) return; // model not bundled
        String[] sentence = {"The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog"};
        // When
        PennTreebankPOS[] tags = tagger.tag(sentence);
        // Then
        assertNotNull(tags);
        assertEquals(sentence.length, tags.length, "One tag per token");
        for (PennTreebankPOS t : tags) {
            assertNotNull(t, "No token should have a null tag");
        }
        // "The" and "the" should be determiners (DT)
        assertEquals(PennTreebankPOS.DT, tags[0], "Expected DT for 'The'");
        assertEquals(PennTreebankPOS.DT, tags[6], "Expected DT for 'the'");
    }

    @Test
    public void testDefaultTaggerReturnsSameInstanceOnSecondCall() {
        // Given / When
        HMMPOSTagger t1 = HMMPOSTagger.getDefault();
        HMMPOSTagger t2 = HMMPOSTagger.getDefault();
        // Then: lazy singleton — same reference
        assertSame(t1, t2);
    }

    // -----------------------------------------------------------------------
    // Cross-validated accuracy on PennTreebank corpora (skipped if data absent)
    // -----------------------------------------------------------------------

    @Test
    public void testWSJ() throws IOException {
        // Given
        MathEx.setSeed(19650218);
        List<String[]> sentences = new ArrayList<>();
        List<PennTreebankPOS[]> tags = new ArrayList<>();
        HMMPOSTagger.read("nlp/src/test/resources/data/PennTreebank/PennTreebank2/TAGGED/POS/WSJ", sentences, tags);
        if (sentences.isEmpty()) return; // data not available — skip

        String[][] x = sentences.toArray(new String[0][]);
        PennTreebankPOS[][] y = tags.toArray(new PennTreebankPOS[0][]);

        // When: 10-fold cross-validation
        int k = 10;
        int error = 0;
        int total = 0;
        Bag[] bags = CrossValidation.of(x.length, k);
        for (int i = 0; i < k; i++) {
            HMMPOSTagger tagger = HMMPOSTagger.fit(MathEx.slice(x, bags[i].samples()), MathEx.slice(y, bags[i].samples()));
            for (int j = 0; j < bags[i].oob().length; j++) {
                String[] sent = x[bags[i].oob()[j]];
                PennTreebankPOS[] pred = tagger.tag(sent);
                PennTreebankPOS[] gold = y[bags[i].oob()[j]];
                total += pred.length;
                for (int l = 0; l < pred.length; l++) {
                    if (pred[l] != gold[l]) error++;
                }
            }
        }

        // Then: error count within expected range
        System.out.format("WSJ error rate = %.2f%% (%d / %d)%n", 100.0 * error / total, error, total);
        assertEquals(51325, error, 50);
    }

    @Test
    public void testBrown() throws IOException {
        // Given
        MathEx.setSeed(19650218);
        List<String[]> sentences = new ArrayList<>();
        List<PennTreebankPOS[]> tags = new ArrayList<>();
        HMMPOSTagger.read("nlp/src/test/resources/data/PennTreebank/PennTreebank2/TAGGED/POS/BROWN", sentences, tags);
        if (sentences.isEmpty()) return; // data not available — skip

        String[][] x = sentences.toArray(new String[0][]);
        PennTreebankPOS[][] y = tags.toArray(new PennTreebankPOS[0][]);

        // When: 10-fold cross-validation
        int k = 10;
        int error = 0;
        int total = 0;
        Bag[] bags = CrossValidation.of(x.length, k);
        for (int i = 0; i < k; i++) {
            HMMPOSTagger tagger = HMMPOSTagger.fit(MathEx.slice(x, bags[i].samples()), MathEx.slice(y, bags[i].samples()));
            for (int j = 0; j < bags[i].oob().length; j++) {
                String[] sent = x[bags[i].oob()[j]];
                PennTreebankPOS[] pred = tagger.tag(sent);
                PennTreebankPOS[] gold = y[bags[i].oob()[j]];
                total += pred.length;
                for (int l = 0; l < pred.length; l++) {
                    if (pred[l] != gold[l]) error++;
                }
            }
        }

        // Then: error count within expected range
        System.out.format("Brown error rate = %.2f%% (%d / %d)%n", 100.0 * error / total, error, total);
        assertEquals(55589, error, 50);
    }
}
