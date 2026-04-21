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
package smile.feature.selection;

import smile.classification.DecisionTree;
import smile.datasets.Abalone;
import smile.datasets.ImageSegmentation;
import smile.gap.BitString;
import smile.gap.Crossover;
import smile.gap.Fitness;
import smile.gap.Selection;
import smile.regression.RegressionTree;
import smile.math.MathEx;
import smile.validation.metric.Accuracy;
import smile.validation.metric.RMSE;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GAFETest {
    
    public GAFETest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
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
/* GAFE with LDA is too slow on Windows as OpenBlas is 4X slower on Windows.
    @Test
    public void testLDA() {
        System.out.println("LDA");

        GAFE selection = new GAFE();
        BitString[] result = selection.apply(50, 10, 256,
                GAFE.fitness(USPS.x, USPS.y, USPS.testx, USPS.testy, new Accuracy(), LDA::fit));
            
        for (BitString bits : result) {
            System.out.format("%.2f%% %s%n", 100*bits.fitness(), bits);
        }

        assertEquals(0.8789, result[result.length-1].fitness(), 1E-4);
    }
*/
    @Test
    public void testDecisionTree() throws Exception {
        System.out.println("DecisionTree");
        var segment = new ImageSegmentation();
        GAFE selection = new GAFE();
        BitString[] result = selection.apply(50, 10, segment.train().ncol()-1,
                GAFE.fitness("class", segment.train(), segment.test(), new Accuracy(), DecisionTree::fit));

        for (BitString bits : result) {
            System.out.format("%.2f%% %s%n", 100*bits.fitness(), bits);
        }

        assertEquals(0.9654, result[result.length-1].fitness(), 1E-4);
    }

    @Test
    public void testRegressionTree() throws Exception {
        System.out.println("RegressionTree");

        GAFE selection = new GAFE();
        var abalone = new Abalone();
        BitString[] result = selection.apply(50, 10, abalone.train().ncol()-1,
                GAFE.fitness("rings", abalone.train(), abalone.test(), new RMSE(), RegressionTree::fit));

        for (BitString bits : result) {
            System.out.format("%.4f %s%n", -bits.fitness(), bits);
        }

        assertEquals(2.2278, -result[result.length-1].fitness(), 1E-4);
    }

    @Test
    public void testGivenZeroPopulationSizeWhenApplyingThenIllegalArgumentException() {
        GAFE selection = new GAFE();
        Fitness<BitString> dummyFitness = bits -> 0.0;
        assertThrows(IllegalArgumentException.class,
                () -> selection.apply(0, 10, 5, dummyFitness));
    }

    @Test
    public void testGivenAllZeroBitsWhenEvaluatingClassificationFitnessThenZeroFitness() {
        // When all bits are 0 (no features selected), classification fitness should return 0.0
        double[][] x     = {{1.0}, {2.0}, {3.0}, {4.0}};
        int[]      y     = {0, 0, 1, 1};
        double[][] testx = {{1.5}, {3.5}};
        int[]      testy = {0, 1};

        Fitness<BitString> fitness = GAFE.fitness(x, y, testx, testy, new Accuracy(),
                (data, labels) -> { throw new AssertionError("trainer should not be called"); });

        BitString allZeros = new BitString(1,
                fitness,
                Crossover.TWO_POINT,
                1.0,
                0.01);

        // Force all bits to 0 by evaluating a manually constructed chromosome
        // We can verify the formula: when bits are all 0, fitness returns 0.0
        // (The all-zeros chromosome returns 0.0 from the lambda without calling the trainer)
        // We verify via the fitness lambda directly.
        double result = fitness.score(new BitString(1, fitness, Crossover.TWO_POINT, 1.0, 0.01) {
            @Override
            public byte[] bits() {
                return new byte[]{0};
            }
        });
        assertEquals(0.0, result, 0.0, "all-zero chromosome should have fitness 0.0");
    }

    @Test
    public void testGivenAllZeroBitsWhenEvaluatingRegressionFitnessThenNegativeInfinity() {
        // When all bits are 0 (no features selected), regression fitness should return -Infinity
        double[][] x     = {{1.0}, {2.0}, {3.0}, {4.0}};
        double[]   y     = {1.0, 2.0, 3.0, 4.0};
        double[][] testx = {{1.5}, {3.5}};
        double[]   testy = {1.5, 3.5};

        Fitness<BitString> fitness = GAFE.fitness(x, y, testx, testy, new RMSE(),
                (data, labels) -> { throw new AssertionError("trainer should not be called"); });

        double result = fitness.score(new BitString(1, fitness, Crossover.TWO_POINT, 1.0, 0.01) {
            @Override
            public byte[] bits() {
                return new byte[]{0};
            }
        });
        assertEquals(Double.NEGATIVE_INFINITY, result, "all-zero regression chromosome should have fitness -Infinity");
    }

    @Test
    public void testGivenCustomGAFEParamsWhenApplyingThenPopulationSizeIsRespected() throws Exception {
        var segment = new ImageSegmentation();
        GAFE selection = new GAFE(
                Selection.Tournament(3, 0.95),
                1,
                Crossover.TWO_POINT,
                1.0,
                0.01
        );
        BitString[] result = selection.apply(20, 5, segment.train().ncol() - 1,
                GAFE.fitness("class", segment.train(), segment.test(), new Accuracy(), DecisionTree::fit));
        assertEquals(20, result.length, "result should have exactly the population size");
    }
}
