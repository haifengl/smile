/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

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
import smile.validation.CrossValidation;
import smile.validation.Error;
import smile.validation.Validation;

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

        WinsorScaler scaler = WinsorScaler.fit(PenDigits.x, 0.01, 0.99);
        double[][] x = scaler.transform(PenDigits.x);

        int p = x[0].length;
        int k = MathEx.max(PenDigits.y) + 1;

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, x, PenDigits.y, (xi, yi) -> {
            MLP model = new MLP(p,
                    Layer.sigmoid(50),
                    Layer.mle(k, OutputFunction.SIGMOID)
            );

            for (int epoch = 1; epoch <= 8; epoch++) {
                int[] permutation = MathEx.permutate(xi.length);
                for (int i : permutation) {
                    model.update(xi[i], yi[i]);
                }
            }

            return model;
        });

        int error = Error.of(PenDigits.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(137, error);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        WinsorScaler scaler = WinsorScaler.fit(BreastCancer.x, 0.01, 0.99);
        double[][] x = scaler.transform(BreastCancer.x);

        int p = x[0].length;

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, x, BreastCancer.y, (xi, yi) -> {
            MLP model = new MLP(p,
                    Layer.sigmoid(60),
                    Layer.mle(1, OutputFunction.SIGMOID)
            );

            model.setLearningRate(0.1);
            model.setMomentum(0.1);

            for (int epoch = 1; epoch <= 8; epoch++) {
                int[] permutation = MathEx.permutate(xi.length);
                for (int i : permutation) {
                    model.update(xi[i], yi[i]);
                }
            }

            return model;
        });

        int error = Error.of(BreastCancer.y, prediction);
        System.out.println("Error = " + error);
        assertEquals(13, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        MathEx.setSeed(19650218); // to get repeatable results.

        WinsorScaler scaler = WinsorScaler.fit(Segment.x, 0.01, 0.99);
        double[][] x = scaler.transform(Segment.x);
        double[][] testx = scaler.transform(Segment.testx);
        int p = x[0].length;
        int k = MathEx.max(Segment.y) + 1;

        System.out.format("----- Online Learning -----%n");
        MLP model = new MLP(p,
                Layer.sigmoid(50),
                Layer.mle(k, OutputFunction.SOFTMAX)
        );

        model.setLearningRate(0.2);
        model.setMomentum(0.2);

        int error = 0;
        for (int epoch = 1; epoch <= 30; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            for (int i : permutation) {
                model.update(x[i], Segment.y[i]);
            }

            int[] prediction = Validation.test(model, testx);
            error = Error.of(Segment.testy, prediction);
            System.out.println("Test Error = " + error);
        }
        assertEquals(36, error);

        System.out.format("----- Mini-Batch Learning -----%n");
        model = new MLP(p,
                Layer.sigmoid(50),
                Layer.mle(k, OutputFunction.SOFTMAX)
        );

        model.setLearningRate(0.3);
        model.setMomentum(0.0);

        int batch = 20;
        double[][] batchx = new double[batch][];
        int[] batchy = new int[batch];
        for (int epoch = 1; epoch <= 11; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            int i = 0;
            for (; i < x.length-batch;) {
                for (int j = 0; j < batch; j++, i++) {
                    batchx[j] = x[permutation[i]];
                    batchy[j] = Segment.y[permutation[i]];
                }
                model.update(batchx, batchy);
            }

            for (; i < x.length; i++) {
                model.update(x[permutation[i]], Segment.y[permutation[i]]);
            }

            int[] prediction = Validation.test(model, testx);
            error = Error.of(Segment.testy, prediction);
            System.out.println("Test Error = " + error);
        }

        assertEquals(33, error);
    }

    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        MathEx.setSeed(19650218); // to get repeatable results.

        Standardizer scaler = Standardizer.fit(USPS.x);
        double[][] x = scaler.transform(USPS.x);
        double[][] testx = scaler.transform(USPS.testx);
        int p = x[0].length;
        int k = MathEx.max(USPS.y) + 1;

        MLP model = new MLP(p,
                Layer.sigmoid(768),
                Layer.sigmoid(192),
                Layer.sigmoid(30),
                Layer.mle(k, OutputFunction.SIGMOID)
        );

        model.setLearningRate(0.1);
        model.setMomentum(0.0);

        int error = 0;
        for (int epoch = 1; epoch <= 5; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            for (int i : permutation) {
                model.update(x[i], USPS.y[i]);
            }

            int[] prediction = Validation.test(model, testx);
            error = Error.of(USPS.testy, prediction);
            System.out.println("Test Error = " + error);
        }

        assertEquals(146, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}