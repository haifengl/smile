/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.classification;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.base.mlp.*;
import smile.data.BreastCancer;
import smile.data.PenDigits;
import smile.data.Segment;
import smile.data.USPS;
import smile.feature.Standardizer;
import smile.feature.WinsorScaler;
import smile.math.MathEx;
import smile.math.TimeFunction;
import smile.validation.ClassificationValidations;
import smile.validation.CrossValidation;
import smile.validation.metric.Error;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class MLPTest {

    public MLPTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testPenDigits() {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.

        WinsorScaler scaler = WinsorScaler.fit(PenDigits.x, 0.01, 0.99);
        double[][] x = scaler.transform(PenDigits.x);
        int p = x[0].length;
        int k = MathEx.max(PenDigits.y) + 1;

        ClassificationValidations<MLP> result = CrossValidation.classification(10, x, PenDigits.y, (xi, yi) -> {
            MLP model = new MLP(Layer.input(p),
                    Layer.sigmoid(50),
                    Layer.mle(k, OutputFunction.SOFTMAX)
            );

            model.setLearningRate(TimeFunction.linear(0.2, 10000, 0.1));
            model.setMomentum(TimeFunction.constant(0.5));

            for (int epoch = 1; epoch <= 8; epoch++) {
                int[] permutation = MathEx.permutate(xi.length);
                for (int i : permutation) {
                    model.update(xi[i], yi[i]);
                }
            }

            return model;
        });

        System.out.println(result);
        assertEquals(0.9847, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");
        MathEx.setSeed(19650218); // to get repeatable results.

        WinsorScaler scaler = WinsorScaler.fit(BreastCancer.x, 0.01, 0.99);
        double[][] x = scaler.transform(BreastCancer.x);
        int p = x[0].length;

        ClassificationValidations<MLP> result = CrossValidation.classification(10, x, BreastCancer.y, (xi, yi) -> {
            MLP model = new MLP(Layer.input(p),
                    Layer.sigmoid(60),
                    Layer.mle(1, OutputFunction.SIGMOID)
            );

            model.setLearningRate(TimeFunction.linear(0.2, 1000, 0.1));
            model.setMomentum(TimeFunction.constant(0.2));

            for (int epoch = 1; epoch <= 8; epoch++) {
                int[] permutation = MathEx.permutate(xi.length);
                for (int i : permutation) {
                    model.update(xi[i], yi[i]);
                }
            }

            return model;
        });

        System.out.println(result);
        assertEquals(0.9773, result.avg.accuracy, 1E-4);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment SGD");
        MathEx.setSeed(19650218); // to get repeatable results.

        WinsorScaler scaler = WinsorScaler.fit(Segment.x, 0.01, 0.99);
        double[][] x = scaler.transform(Segment.x);
        double[][] testx = scaler.transform(Segment.testx);
        int p = x[0].length;
        int k = MathEx.max(Segment.y) + 1;

        MLP model = new MLP(Layer.input(p),
                Layer.sigmoid(50),
                Layer.mle(k, OutputFunction.SOFTMAX)
        );

        model.setLearningRate(TimeFunction.constant(0.2));

        int error = 0;
        for (int epoch = 1; epoch <= 30; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            for (int i : permutation) {
                model.update(x[i], Segment.y[i]);
            }

            int[] prediction = model.predict(testx);
            error = Error.of(Segment.testy, prediction);
            System.out.println("Test Error = " + error);
        }
        assertEquals(30, error);
    }

    @Test
    public void testSegmentMiniBatch() {
        System.out.println("Segment Mini-Batch");
        MathEx.setSeed(19650218); // to get repeatable results.

        WinsorScaler scaler = WinsorScaler.fit(Segment.x, 0.01, 0.99);
        double[][] x = scaler.transform(Segment.x);
        double[][] testx = scaler.transform(Segment.testx);
        int p = x[0].length;
        int k = MathEx.max(Segment.y) + 1;

        MLP model = new MLP(Layer.input(p),
                Layer.sigmoid(50),
                Layer.mle(k, OutputFunction.SOFTMAX)
        );

        model.setLearningRate(TimeFunction.linear(0.1, 10000, 0.01));
        model.setRMSProp(0.9, 1E-7);

        int batch = 20;
        double[][] batchx = new double[batch][];
        int[] batchy = new int[batch];
        int error = 0;
        for (int epoch = 1; epoch <= 13; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            int i = 0;
            while (i < x.length-batch) {
                for (int j = 0; j < batch; j++, i++) {
                    batchx[j] = x[permutation[i]];
                    batchy[j] = Segment.y[permutation[i]];
                }
                model.update(batchx, batchy);
            }

            for (; i < x.length; i++) {
                model.update(x[permutation[i]], Segment.y[permutation[i]]);
            }

            int[] prediction = model.predict(testx);
            error = Error.of(Segment.testy, prediction);
            System.out.println("Test Error = " + error);
        }

        assertEquals(28, error);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS SGD");
        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = USPS.x;
        double[][] testx = USPS.testx;
        int p = x[0].length;
        int k = MathEx.max(USPS.y) + 1;

        MLP model = new MLP(Layer.input(p),
                Layer.leaky(768, 0.5, 0.02),
                Layer.rectifier(192),
                Layer.rectifier(30),
                Layer.mle(k, OutputFunction.SOFTMAX)
        );

        model.setLearningRate(TimeFunction.linear(0.01, 20000, 0.001));

        int error = 0;
        for (int epoch = 1; epoch <= 5; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            for (int i : permutation) {
                model.update(x[i], USPS.y[i]);
            }

            int[] prediction = model.predict(testx);
            error = Error.of(USPS.testy, prediction);
            System.out.println("Test Error = " + error);
        }

        assertEquals(109, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testUSPSMiniBatch() {
        System.out.println("USPS Mini-Batch");
        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = USPS.x;
        double[][] testx = USPS.testx;
        int p = x[0].length;
        int k = MathEx.max(USPS.y) + 1;

        MLP model = new MLP(Layer.input(p),
                Layer.sigmoid(768),
                Layer.sigmoid(192),
                Layer.sigmoid(30),
                Layer.mle(k, OutputFunction.SOFTMAX)
        );

        model.setLearningRate(
                TimeFunction.piecewise(
                        new int[]   {2000,  4000,  6000,  8000,  10000},
                        new double[]{0.01, 0.009, 0.008, 0.007, 0.006, 0.005}
                )
        );
        model.setRMSProp(0.9, 1E-7);

        int batch = 32;
        double[][] batchx = new double[batch][];
        int[] batchy = new int[batch];
        int error = 0;
        for (int epoch = 1; epoch <= 10; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            int i = 0;
            while (i < x.length-batch) {
                for (int j = 0; j < batch; j++, i++) {
                    batchx[j] = x[permutation[i]];
                    batchy[j] = USPS.y[permutation[i]];
                }
                model.update(batchx, batchy);
            }

            for (; i < x.length; i++) {
                model.update(x[permutation[i]], USPS.y[permutation[i]]);
            }

            int[] prediction = model.predict(testx);
            error = Error.of(USPS.testy, prediction);
            System.out.println("Test Error = " + error);
        }

        assertEquals(115, error);
    }
}