/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.classification;

import java.io.IOException;
import smile.data.SparseDataset;
import smile.data.SampleInstance;
import smile.io.Write;
import smile.test.data.Segment;
import smile.test.data.USPS;
import smile.data.transform.InvertibleColumnTransform;
import smile.feature.transform.Standardizer;
import smile.io.Read;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.BinarySparseGaussianKernel;
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

        SparseDataset<Integer> train = Read.libsvm(smile.util.Paths.getTestData("libsvm/svmguide1"));
        SparseDataset<Integer> test  = Read.libsvm(smile.util.Paths.getTestData("libsvm/svmguide1.t"));

        int n = train.size();
        double[][] x = new double[n][4];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            SampleInstance<SparseArray, Integer> sample = train.get(i);
            for (SparseArray.Entry e : sample.x()) {
                x[i][e.i] = e.x;
            }
            y[i] = sample.y() > 0 ? +1 : -1;
        }

        n = test.size();
        double[][] testx = new double[n][4];
        int[] testy = new int[n];
        for (int i = 0; i < n; i++) {
            SampleInstance<SparseArray, Integer> sample = test.get(i);
            for (SparseArray.Entry e : sample.x()) {
                testx[i][e.i] = e.x;
            }
            testy[i] = sample.y() > 0 ? +1 : -1;
        }

        GaussianKernel kernel = new GaussianKernel(90);
        SVM<double[]> model = SVM.fit(x, y, kernel, 100, 1E-3, 1);

        int[] prediction = model.predict(testx);
        int error = Error.of(testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / testx.length);
        assertEquals(130, error, 10);
    }
/*
    @Test
    public void testAdult() throws IOException {
        System.out.println("adult");
        MathEx.setSeed(19650218); // to get repeatable results.

        // Adult is not in standard format as its index start with 0.
        SparseDataset<Integer> train = Read.libsvm(smile.util.Paths.getTestData("libsvm/data_lasvm_adult_adult.trn"));
        SparseDataset<Integer> test  = Read.libsvm(smile.util.Paths.getTestData("libsvm/data_lasvm_adult_adult.tst"));

        int n = Math.min(20000, train.size()); // to avoid OOM
        int[][] x = new int[n][];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            SampleInstance<SparseArray, Integer> sample = train.get(i);
            x[i] = new int[sample.x().size()];
            int j = 0;
            for (SparseArray.Entry e : sample.x()) {
                x[i][j++] = e.i + 1; // The file is not standard libsvm format as the index starts with 0.
            }
            y[i] = sample.y();
        }

        n = test.size();
        int[][] testx = new int[n][];
        int[] testy = new int[n];
        for (int i = 0; i < n; i++) {
            SampleInstance<SparseArray, Integer> sample = test.get(i);
            testx[i] = new int[sample.x().size()];
            int j = 0;
            for (SparseArray.Entry e : sample.x()) {
                testx[i][j++] = e.i + 1;
            }
            testy[i] = sample.y();
        }

        BinarySparseGaussianKernel kernel = new BinarySparseGaussianKernel(28);
        Classifier<int[]> model = SVM.fit(x, y, kernel, 100, 1E-3, 1);

        int[] prediction = model.predict(testx);
        int error = Error.of(testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / testx.length);
        assertEquals(2479, error);
    }
*/
    @Test
    public void testSegment() {
        System.out.println("Segment");
        MathEx.setSeed(19650218); // to get repeatable results.

        InvertibleColumnTransform scaler = Standardizer.fit(Segment.train);
        System.out.println(scaler);
        double[][] x = scaler.apply(Segment.formula.x(Segment.train)).toArray();
        double[][] testx = scaler.apply(Segment.formula.x(Segment.test)).toArray();

        GaussianKernel kernel = new GaussianKernel(6.4);
        OneVersusOne<double[]> model = OneVersusOne.fit(x, Segment.y, (xi, y) -> SVM.fit(xi, y, kernel, 100, 1E-3, 1));

        int[] prediction = model.predict(testx);
        int error = Error.of(Segment.testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / Segment.testx.length);
        assertEquals(33, error, 3);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.

        GaussianKernel kernel = new GaussianKernel(8.0);
        OneVersusRest<double[]> model = OneVersusRest.fit(USPS.x, USPS.y, (x, y) -> SVM.fit(x, y, kernel, 5, 1E-3, 1));

        int[] prediction = model.predict(USPS.testx);
        int error = Error.of(USPS.testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / USPS.testx.length);
        assertEquals(86, error, 5);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
