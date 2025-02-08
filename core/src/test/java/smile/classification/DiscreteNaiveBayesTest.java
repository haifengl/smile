/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.function.Function;
import smile.feature.extraction.BagOfWords;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.validation.Bag;
import smile.validation.CrossValidation;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
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
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testBatchMultinomial() throws Exception {
        System.out.println("---Batch Multinomial---");

        MathEx.setSeed(19650218); // to get repeatable results.
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

        MathEx.setSeed(19650218); // to get repeatable results.
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

        MathEx.setSeed(19650218); // to get repeatable results.
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

        MathEx.setSeed(19650218); // to get repeatable results.
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

        MathEx.setSeed(19650218); // to get repeatable results.
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
        assertEquals(309, error);
    }

    @Test
    public void testOnlineBernoulli() {
        System.out.println("---Online Bernoulli---");

        MathEx.setSeed(19650218); // to get repeatable results.
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
        assertEquals(309, error);
    }

    @Test
    public void testBatchCNB() {
        System.out.println("---Batch CNB---");

        MathEx.setSeed(19650218); // to get repeatable results.
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
        assertEquals(313, error);
    }

    @Test
    public void testOnlineCNB() {
        System.out.println("---Online CNB---");

        MathEx.setSeed(19650218); // to get repeatable results.
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
        assertEquals(313, error);
    }

    @Test
    public void testBatchWCNB() {
        System.out.println("---Batch WCNB---");

        MathEx.setSeed(19650218); // to get repeatable results.
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
        assertEquals(315, error);
    }

    @Test
    public void testOnlineWCNB() {
        System.out.println("---Online WCNB---");

        MathEx.setSeed(19650218); // to get repeatable results.
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
        assertEquals(315, error);
    }

    @Test
    public void testBatchTWCNB() {
        System.out.println("---Batch TWCNB---");

        MathEx.setSeed(19650218); // to get repeatable results.
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
        assertEquals(316, error);
    }
}
