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

import java.io.IOException;
import smile.data.SparseDataset;
import smile.data.SampleInstance;
import smile.datasets.ImageSegmentation;
import smile.datasets.USPS;
import smile.data.transform.InvertibleColumnTransform;
import smile.feature.transform.Standardizer;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.util.SparseArray;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SVMTest {

    public SVMTest() {
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
    public void testSVMGuide1() throws IOException {
        System.out.println("svmguide1");
        MathEx.setSeed(19650218); // to get repeatable results.

        SparseDataset<Integer> train = Read.libsvm(smile.io.Paths.getTestData("libsvm/svmguide1"));
        SparseDataset<Integer> test  = Read.libsvm(smile.io.Paths.getTestData("libsvm/svmguide1.t"));

        int n = train.size();
        double[][] x = new double[n][4];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            SampleInstance<SparseArray, Integer> sample = train.get(i);
            for (SparseArray.Entry e : sample.x()) {
                x[i][e.index()] = e.value();
            }
            y[i] = sample.y() > 0 ? +1 : -1;
        }

        n = test.size();
        double[][] testx = new double[n][4];
        int[] testy = new int[n];
        for (int i = 0; i < n; i++) {
            SampleInstance<SparseArray, Integer> sample = test.get(i);
            for (SparseArray.Entry e : sample.x()) {
                testx[i][e.index()] = e.value();
            }
            testy[i] = sample.y() > 0 ? +1 : -1;
        }

        GaussianKernel kernel = new GaussianKernel(90);
        SVM<double[]> model = SVM.fit(x, y, kernel, new SVM.Options(100));

        int[] prediction = model.predict(testx);
        int error = Error.of(testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / testx.length);
        assertEquals(130, error, 10);
    }

    @Test
    public void testSegment() throws Exception {
        System.out.println("Segment");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        InvertibleColumnTransform scaler = Standardizer.fit(segment.train());
        System.out.println(scaler);
        double[][] x = scaler.apply(segment.formula().x(segment.train())).toArray();
        double[][] testx = scaler.apply(segment.formula().x(segment.test())).toArray();

        GaussianKernel kernel = new GaussianKernel(6.4);
        var options = new SVM.Options(100);
        OneVersusOne<double[]> model = OneVersusOne.fit(x, segment.y(), (xi, y) -> SVM.fit(xi, y, kernel, options));

        int[] prediction = model.predict(testx);
        int error = Error.of(segment.testy(), prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / segment.testx().length);
        assertEquals(33, error, 3);
    }

    @Test
    public void testLinearSegment() throws Exception {
        System.out.println("Linear Segment");
        MathEx.setSeed(19650218); // to get repeatable results.
        var segment = new ImageSegmentation();
        InvertibleColumnTransform scaler = Standardizer.fit(segment.train());
        System.out.println(scaler);
        double[][] x = scaler.apply(segment.formula().x(segment.train())).toArray();
        double[][] testx = scaler.apply(segment.formula().x(segment.test())).toArray();

        var options = new SVM.Options(100);
        OneVersusRest<double[]> model = OneVersusRest.fit(x, segment.y(), (xi, y) -> SVM.fit(xi, y, options));

        int[] prediction = model.predict(testx);
        int error = Error.of(segment.testy(), prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / segment.testx().length);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.
        var usps = new USPS();
        GaussianKernel kernel = new GaussianKernel(8.0);
        var options = new SVM.Options(5);
        OneVersusRest<double[]> model = OneVersusRest.fit(usps.x(), usps.y(), (x, y) -> SVM.fit(x, y, kernel, options));

        int[] prediction = model.predict(usps.testx());
        int error = Error.of(usps.testy(), prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / usps.testx().length);
        assertEquals(86, error, 5);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
