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
import smile.base.neuralnetwork.ActivationFunction;
import smile.base.neuralnetwork.Layer;
import smile.base.neuralnetwork.ObjectiveFunction;
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
public class NeuralNetworkTest {

    public NeuralNetworkTest() {
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
            NeuralNetwork model = new NeuralNetwork(ObjectiveFunction.CROSS_ENTROPY,
                    new Layer(ActivationFunction.LOGISTIC_SIGMOID, 30, p),
                    new Layer(ActivationFunction.SOFTMAX, k, 30)
            );

            for (int e = 0; e < 20; e++) {
                int[] permutation = MathEx.permutate(xi.length);
                for (int i : permutation) {
                    model.update(xi[i], yi[i]);
                }
            }

            return model;
        });
        int error = Error.apply(PenDigits.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(884, error);
    }

    @Test
    public void testBreastCancer() {
        System.out.println("Breast Cancer");

        WinsorScaler scaler = WinsorScaler.fit(BreastCancer.x, 0.01, 0.99);
        double[][] x = scaler.transform(BreastCancer.x);

        int p = x[0].length;
        int k = MathEx.max(BreastCancer.y) + 1;

        MathEx.setSeed(19650218); // to get repeatable results.
        int[] prediction = CrossValidation.classification(10, BreastCancer.x, BreastCancer.y, (xi, yi) -> {
            NeuralNetwork model = new NeuralNetwork(ObjectiveFunction.CROSS_ENTROPY,
                    new Layer(ActivationFunction.LOGISTIC_SIGMOID, 30, p),
                    new Layer(ActivationFunction.SOFTMAX, k, 30)
            );

            for (int e = 0; e < 20; e++) {
                int[] permutation = MathEx.permutate(xi.length);
                for (int i : permutation) {
                    model.update(xi[i], yi[i]);
                }
            }

            return model;
        });
        int error = Error.apply(BreastCancer.y, prediction);

        System.out.println("Error = " + error);
        assertEquals(42, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        MathEx.setSeed(19650218); // to get repeatable results.

        WinsorScaler scaler = WinsorScaler.fit(Segment.x, 0.01, 0.99);
        //Standardizer scaler = Standardizer.fit(Segment.x);
        double[][] x = scaler.transform(Segment.x);
        double[][] testx = scaler.transform(Segment.testx);
        int p = x[0].length;
        int k = MathEx.max(Segment.y) + 1;

        NeuralNetwork model = new NeuralNetwork(ObjectiveFunction.CROSS_ENTROPY,
                new Layer(ActivationFunction.LOGISTIC_SIGMOID, 30, p),
                new Layer(ActivationFunction.SOFTMAX, k, 30)
        );

        for (int e = 0; e < 20; e++) {
            int[] permutation = MathEx.permutate(x.length);
            for (int i : permutation) {
                model.update(x[i], Segment.y[i]);
            }
        }

        int[] prediction = Validation.test(model, testx);
        int error = Error.apply(Segment.testy, prediction);
        System.out.println("Online Error = " + error);
        //assertEquals(123, error);

        model = new NeuralNetwork(ObjectiveFunction.CROSS_ENTROPY,
                new Layer(ActivationFunction.RECTIFIER, 30, p),
                new Layer(ActivationFunction.RECTIFIER, 50, 30),
                new Layer(ActivationFunction.SOFTMAX, k, 50)
        );

        int b = 50;
        double[][] batchx = new double[b][];
        int[] batchy = new int[b];
        for (int e = 0; e < 5; e++) {
            int i = 0;
            for (; i < x.length-b; i+=b) {
                System.arraycopy(x, i, batchx, 0, b);
                System.arraycopy(Segment.y, i, batchy, 0, b);
                model.update(batchx, batchy);
            }

            for (; i < x.length; i++) {
                model.update(x[i], Segment.y[i]);
            }
        }

        prediction = Validation.test(model, testx);
        error = Error.apply(Segment.testy, prediction);
        System.out.println("Mini-batch Error = " + error);
        assertEquals(123, error);
    }

    @Test
    public void testUSPS() {
        System.out.println("USPS");

        MathEx.setSeed(19650218); // to get repeatable results.

        WinsorScaler scaler = WinsorScaler.fit(USPS.x);
        double[][] x = scaler.transform(USPS.x);
        double[][] testx = scaler.transform(USPS.testx);
        int p = x[0].length;
        int k = MathEx.max(USPS.y) + 1;

        NeuralNetwork model = new NeuralNetwork(ObjectiveFunction.CROSS_ENTROPY,
                new Layer(ActivationFunction.HYPERBOLIC_TANGENT, 40, p),
                new Layer(ActivationFunction.SOFTMAX, k, 40)
        );

        int b = 50;
        double[][] batchx = new double[b][];
        int[] batchy = new int[b];
        for (int e = 0; e < 10; e++) {
            for (int i = 0; i < x.length-b; i+=b) {
                System.arraycopy(x, i, batchx, 0, b);
                System.arraycopy(USPS.y, i, batchy, 0, b);
                model.update(batchx, batchy);
            }
        }

        int[] prediction = Validation.test(model, testx);
        int error = Error.apply(USPS.testy, prediction);
        System.out.println("Online Error = " + error);
        assertEquals(123, error);
    }
}