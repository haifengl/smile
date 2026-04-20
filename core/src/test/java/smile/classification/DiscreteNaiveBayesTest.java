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
package smile.classification;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.function.Function;
import smile.feature.extraction.BagOfWords;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.util.IntSet;
import smile.validation.Bag;
import smile.validation.CrossValidation;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DiscreteNaiveBayes focusing on label handling and
 * the correctness of predict when labels are not 0-based.
 * @author Haifeng Li
 */
public class DiscreteNaiveBayesTest {

    private static final String[] feature = {
            "outstanding", "wonderfully", "wasted", "lame", "awful", "poorly",
            "ridiculous", "waste", "worst", "bland", "unfunny", "stupid", "dull",
            "fantastic", "laughable", "mess", "pointless", "terrific", "memorable",
            "superb", "boring", "badly", "subtle", "terrible", "excellent",
            "perfectly", "masterpiece", "realistic", "flaws", "enjoyable", "funniest",
            "loved", "amazing", "favorite", "perfect", "poor", "worse", "horrible",
            "disappointing", "disappointment"
    };

    private static final String[] doc = new String[2000];
    private static final int[][] x = new int[doc.length][];
    private static final int[] y = new int[doc.length];
    private static final Function<String, String[]> tokenizer = s -> s.split("\\s+");

    public DiscreteNaiveBayesTest() {

    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        try (BufferedReader input = smile.io.Paths.getTestDataReader("text/movie.txt")) {
            for (int i = 0; i < x.length; i++) {
                String[] words = input.readLine().trim().split("\\s+", 2);

                if (words[0].equalsIgnoreCase("pos")) {
                    y[i] = 1;
                } else if (words[0].equalsIgnoreCase("neg")) {
                    y[i] = 0;
                } else {
                    System.err.println("Invalid class label: " + words[0]);
                }

                doc[i] = words[1];
            }

            BagOfWords bag = new BagOfWords(tokenizer, feature);
            for (int i = 0; i < x.length; i++) {
                x[i] = bag.apply(doc[i]);
            }
        } catch (Exception ex) {
            System.err.println("Failed to load 'movie': " + ex);
            System.exit(-1);
        }
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testBatchMultinomial() throws Exception {
        System.out.println("---Batch Multinomial---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, feature.length);
            bayes.update(MathEx.slice(x, bag.samples()), MathEx.slice(y, bag.samples()));

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(316, error);

        DiscreteNaiveBayes model = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, feature.length);
        model.update(x, y);
        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testOnlineMultinomial() {
        System.out.println("---Online Multinomial---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, feature.length);

            for (int i : bag.samples()) {
                bayes.update(x[i], y[i]);
            }

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(316, error);
    }

    @Test
    public void testBatchPolyaUrn() {
        System.out.println("---Batch PolyaUrn---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.POLYAURN, 2, feature.length);
            bayes.update(MathEx.slice(x, bag.samples()), MathEx.slice(y, bag.samples()));

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(316, error);
    }

    @Test
    public void testOnlinePolyaUrn() {
        System.out.println("---Online PolyaUrn---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.POLYAURN, 2, feature.length);

            for (int i : bag.samples()) {
                bayes.update(x[i], y[i]);
            }

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(316, error);
    }

    @Test
    public void testBatchBernoulli() {
        System.out.println("---Batch Bernoulli---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.BERNOULLI, 2, feature.length);
            bayes.update(MathEx.slice(x, bag.samples()), MathEx.slice(y, bag.samples()));

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(307, error);
    }

    @Test
    public void testOnlineBernoulli() {
        System.out.println("---Online Bernoulli---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.BERNOULLI, 2, feature.length);

            for (int i : bag.samples()) {
                bayes.update(x[i], y[i]);
            }

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(307, error);
    }

    @Test
    public void testBatchCNB() {
        System.out.println("---Batch CNB---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.CNB, 2, feature.length);
            bayes.update(MathEx.slice(x, bag.samples()), MathEx.slice(y, bag.samples()));

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(315, error);
    }

    @Test
    public void testOnlineCNB() {
        System.out.println("---Online CNB---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.CNB, 2, feature.length);

            for (int i : bag.samples()) {
                bayes.update(x[i], y[i]);
            }

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(315, error);
    }

    @Test
    public void testBatchWCNB() {
        System.out.println("---Batch WCNB---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.WCNB, 2, feature.length);
            bayes.update(MathEx.slice(x, bag.samples()), MathEx.slice(y, bag.samples()));

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(314, error);
    }

    @Test
    public void testOnlineWCNB() {
        System.out.println("---Online WCNB---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.WCNB, 2, feature.length);

            for (int i : bag.samples()) {
                bayes.update(x[i], y[i]);
            }

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(314, error);
    }

    @Test
    public void testBatchTWCNB() {
        System.out.println("---Batch TWCNB---");

        Bag[] bags = CrossValidation.of(x.length, 10);
        int[] prediction = new int[x.length];
        for (Bag bag : bags) {
            DiscreteNaiveBayes bayes = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.TWCNB, 2, feature.length);
            bayes.update(MathEx.slice(x, bag.samples()), MathEx.slice(y, bag.samples()));

            for (int i : bag.oob()) {
                prediction[i] = bayes.predict(x[i]);
            }
        }

        // discount the instance without any feature words.
        int nulls = (int) Arrays.stream(prediction).filter(y -> y == Integer.MIN_VALUE).count();
        int error = Error.of(y, prediction) - nulls;
        System.out.format("Error = %d out of %d%n", error, x.length - nulls);
        assertEquals(310, error);
    }

    /**
     * Two-feature vocabulary, two classes with labels {10, 20}.
     * Documents in class 10 contain mostly term 0; class 20 mostly term 1.
     */
    private DiscreteNaiveBayes buildModelWithNonContiguousLabels(DiscreteNaiveBayes.Model model) {
        // Classes 10 and 20 — not 0-indexed
        IntSet labels = new IntSet(new int[]{10, 20});
        DiscreteNaiveBayes nb = new DiscreteNaiveBayes(model, 2, 2, 1.0, labels);

        int[][] x = {
                {5, 0},  // clearly class 10
                {4, 0},  // clearly class 10
                {0, 5},  // clearly class 20
                {0, 4},  // clearly class 20
        };
        int[] y = {10, 10, 20, 20};
        nb.update(x, y);
        return nb;
    }

    // ── predict(int[]) correctness — bug fix: must return original label ────────

    @Test
    void givenNonContiguousLabels_whenPredict_thenReturnsOriginalLabel() {
        // This test directly verifies the bug fix in predict(int[], double[]).
        // Before the fix, predict() returned the internal index (0 or 1)
        // instead of the original class label (10 or 20).
        DiscreteNaiveBayes nb = buildModelWithNonContiguousLabels(DiscreteNaiveBayes.Model.MULTINOMIAL);

        int predFor10 = nb.predict(new int[]{5, 0});  // should be 10
        int predFor20 = nb.predict(new int[]{0, 5});  // should be 20

        assertTrue(predFor10 == 10 || predFor10 == 20,
                "Predicted label must be one of the original labels, got: " + predFor10);
        assertTrue(predFor20 == 10 || predFor20 == 20,
                "Predicted label must be one of the original labels, got: " + predFor20);

        assertEquals(10, predFor10, "Term-0-heavy doc should be predicted as class 10");
        assertEquals(20, predFor20, "Term-1-heavy doc should be predicted as class 20");
    }

    @Test
    void givenNonContiguousLabels_whenPredictWithPosteriori_thenReturnsOriginalLabel() {
        DiscreteNaiveBayes nb = buildModelWithNonContiguousLabels(DiscreteNaiveBayes.Model.MULTINOMIAL);

        double[] posteriori = new double[2];
        int pred = nb.predict(new int[]{5, 0}, posteriori);

        assertTrue(pred == 10 || pred == 20,
                "Predicted label must be one of {10, 20}, got: " + pred);
        assertEquals(pred, pred == 10 ? 10 : 20);

        // posteriori must sum to ≈ 1
        assertEquals(1.0, posteriori[0] + posteriori[1], 1E-9);
    }

    // ── isSoft / isOnline ─────────────────────────────────────────────────────

    @Test
    void givenAnyModel_whenIsSoft_thenTrue() {
        DiscreteNaiveBayes nb = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, 3);
        assertTrue(nb.isSoft());
    }

    @Test
    void givenMultinomialOrBernoulli_whenIsOnline_thenTrue() {
        assertTrue(new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, 3).isOnline());
        assertTrue(new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.BERNOULLI, 2, 3).isOnline());
    }

    @Test
    void givenTWCNB_whenOnlineUpdate_thenThrowsUnsupportedOperationException() {
        DiscreteNaiveBayes nb = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.TWCNB, 2, 3);
        assertThrows(UnsupportedOperationException.class,
                () -> nb.update(new int[]{1, 0, 0}, 0));
    }

    // ── all model variants accept non-contiguous labels ───────────────────────

    @Test
    void givenBernoulliWithNonContiguousLabels_whenPredict_thenReturnsOriginalLabel() {
        DiscreteNaiveBayes nb = buildModelWithNonContiguousLabels(DiscreteNaiveBayes.Model.BERNOULLI);
        int pred = nb.predict(new int[]{1, 0});
        assertTrue(pred == 10 || pred == 20);
        assertEquals(10, pred);
    }

    @Test
    void givenPolyaUrnWithNonContiguousLabels_whenPredict_thenReturnsOriginalLabel() {
        DiscreteNaiveBayes nb = buildModelWithNonContiguousLabels(DiscreteNaiveBayes.Model.POLYAURN);
        int pred = nb.predict(new int[]{0, 1});
        assertTrue(pred == 10 || pred == 20);
        assertEquals(20, pred);
    }

    // ── TWCNB batch with non-contiguous labels ────────────────────────────────

    @Test
    void givenTWCNBWithNonContiguousLabels_whenBatchUpdate_thenPredictsOriginalLabel() {
        // This test exercises the TWCNB batch path where yi must use
        // classes.indexOf(y[i]) rather than y[i] directly.
        IntSet labels = new IntSet(new int[]{10, 20});
        DiscreteNaiveBayes nb = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.TWCNB, 2, 5, 1.0, labels);

        int[][] x = {
                {5, 4, 0, 0, 0},   // class 10
                {4, 5, 0, 0, 0},   // class 10
                {0, 0, 5, 4, 0},   // class 20
                {0, 0, 4, 5, 0},   // class 20
        };
        int[] y = {10, 10, 20, 20};

        // Before fix: int yi = y[i] would use 10 or 20 as row index → AIOOBE
        assertDoesNotThrow(() -> nb.update(x, y),
                "TWCNB batch update must not throw for non-contiguous labels");

        // After training, predictions must return original labels
        int pred10 = nb.predict(new int[]{5, 4, 0, 0, 0});
        int pred20 = nb.predict(new int[]{0, 0, 5, 4, 0});

        assertTrue(pred10 == 10 || pred10 == 20);
        assertTrue(pred20 == 10 || pred20 == 20);
    }

    // ── constructor validation ─────────────────────────────────────────────────

    @Test
    void givenKLessThanTwo_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 1, 5));
    }

    @Test
    void givenNonPositiveDimension_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, 0));
    }

    @Test
    void givenNegativeSigma_whenConstructing_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, 2, 3, -0.1,
                        IntSet.of(2)));
    }

    @Test
    void givenBadPriori_whenConstructingWithFixed_thenThrowsIllegalArgumentException() {
        // Sum != 1
        assertThrows(IllegalArgumentException.class,
                () -> new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL,
                        new double[]{0.3, 0.3}, 3));
        // Negative probability
        assertThrows(IllegalArgumentException.class,
                () -> new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL,
                        new double[]{-0.3, 1.3}, 3));
    }

    // ── empty instance ────────────────────────────────────────────────────────

    @Test
    void givenAllZeroInstance_whenPredict_thenReturnsIntMinValue() {
        DiscreteNaiveBayes nb = buildModelWithNonContiguousLabels(DiscreteNaiveBayes.Model.MULTINOMIAL);
        assertEquals(Integer.MIN_VALUE, nb.predict(new int[]{0, 0}),
                "All-zero instance should return Integer.MIN_VALUE");
    }
}
