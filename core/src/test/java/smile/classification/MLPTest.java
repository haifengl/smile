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

import smile.data.DataFrame;
import smile.data.transform.InvertibleColumnTransform;
import smile.datasets.BreastCancer;
import smile.datasets.ImageSegmentation;
import smile.datasets.PenDigits;
import smile.datasets.USPS;
import smile.feature.transform.WinsorScaler;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.model.mlp.*;
import smile.util.function.TimeFunction;
import smile.validation.ClassificationValidations;
import smile.validation.CrossValidation;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class MLPTest {
    public MLPTest() throws Exception {
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
    public void testPenDigits() throws Exception {
        System.out.println("Pen Digits");
        MathEx.setSeed(19650218); // to get repeatable results.
        var pen = new PenDigits();
        DataFrame data = pen.formula().x(pen.data());
        InvertibleColumnTransform scaler = WinsorScaler.fit(data, 0.01, 0.99);
        double[][] x = scaler.apply(data).toArray();
        int[] y = pen.y();
        int p = x[0].length;
        int k = MathEx.max(y) + 1;

        ClassificationValidations<MLP> result = CrossValidation.classification(10, x, y, (xi, yi) -> {
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
        assertEquals(0.9847, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("Breast Cancer");
        MathEx.setSeed(19650218); // to get repeatable results.
        var cancer = new BreastCancer();
        DataFrame data = cancer.formula().x(cancer.data());
        InvertibleColumnTransform scaler = WinsorScaler.fit(data, 0.01, 0.99);
        System.out.println(scaler);
        double[][] x = scaler.apply(data).toArray();
        int p = x[0].length;

        ClassificationValidations<MLP> result = CrossValidation.classification(10, x, cancer.y(), (xi, yi) -> {
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
        assertEquals(0.9773, result.avg().accuracy(), 1E-4);
    }

    @Test
    public void testSegment() throws Exception {
        System.out.println("Segment SGD");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        int[] y = segment.y();
        int[] testy = segment.testy();
        DataFrame data = segment.formula().x(segment.train());
        InvertibleColumnTransform scaler = WinsorScaler.fit(data, 0.01, 0.99);
        System.out.println(scaler);
        double[][] x = scaler.apply(data).toArray();
        double[][] testx = scaler.apply(segment.formula().x(segment.test())).toArray();
        int p = x[0].length;
        int k = MathEx.max(y) + 1;

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
                model.update(x[i], y[i]);
            }

            int[] prediction = model.predict(testx);
            error = Error.of(testy, prediction);
            System.out.println("Test Error = " + error);
        }
        assertEquals(30, error);
    }

    @Test
    public void testSegmentMiniBatch() throws Exception {
        System.out.println("Segment Mini-Batch");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        int[] y = segment.y();
        int[] testy = segment.testy();

        DataFrame data = segment.formula().x(segment.train());
        InvertibleColumnTransform scaler = WinsorScaler.fit(data, 0.01, 0.99);
        System.out.println(scaler);
        double[][] x = scaler.apply(data).toArray();
        double[][] testx = scaler.apply(segment.formula().x(segment.test())).toArray();
        int p = x[0].length;
        int k = MathEx.max(y) + 1;

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
                    batchy[j] = y[permutation[i]];
                }
                model.update(batchx, batchy);
            }

            for (; i < x.length; i++) {
                model.update(x[permutation[i]], y[permutation[i]]);
            }

            int[] prediction = model.predict(testx);
            error = Error.of(testy, prediction);
            System.out.println("Test Error = " + error);
        }

        assertEquals(28, error);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS SGD");
        MathEx.setSeed(19650218); // to get repeatable results.
        var usps = new USPS();
        double[][] x = usps.x();
        int[] y = usps.y();
        double[][] testx = usps.testx();
        int[] testy = usps.testy();

        int p = x[0].length;
        int k = MathEx.max(y) + 1;

        MLP model = new MLP(Layer.input(p),
                Layer.leaky(768, 0.2, 0.02),
                Layer.rectifier(192),
                Layer.rectifier(30),
                Layer.mle(k, OutputFunction.SOFTMAX)
        );

        model.setLearningRate(TimeFunction.linear(0.01, 20000, 0.001));

        int error = 0;
        for (int epoch = 1; epoch <= 3; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            for (int i : permutation) {
                model.update(x[i], y[i]);
            }

            int[] prediction = model.predict(testx);
            error = Error.of(testy, prediction);
            System.out.println("Test Error = " + error);
        }

        assertEquals(115, error, 5);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testUSPSMiniBatch() throws Exception {
        System.out.println("USPS Mini-Batch");
        MathEx.setSeed(19650218); // to get repeatable results.
        var usps = new USPS();
        double[][] x = usps.x();
        int[] y = usps.y();
        double[][] testx = usps.testx();
        int[] testy = usps.testy();

        int p = x[0].length;
        int k = MathEx.max(y) + 1;

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

        int batch = 64;
        double[][] batchx = new double[batch][];
        int[] batchy = new int[batch];
        int error = 0;
        for (int epoch = 1; epoch <= 2; epoch++) {
            System.out.format("----- epoch %d -----%n", epoch);
            int[] permutation = MathEx.permutate(x.length);
            int i = 0;
            while (i < x.length-batch) {
                for (int j = 0; j < batch; j++, i++) {
                    batchx[j] = x[permutation[i]];
                    batchy[j] = y[permutation[i]];
                }
                model.update(batchx, batchy);
            }

            for (; i < x.length; i++) {
                model.update(x[permutation[i]], y[permutation[i]]);
            }

            int[] prediction = model.predict(testx);
            error = Error.of(testy, prediction);
            System.out.println("Test Error = " + error);
        }

        assertEquals(180, error, 5);
    }
}
