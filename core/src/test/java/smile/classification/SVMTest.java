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
import static org.junit.Assert.*;
import java.io.IOException;
import smile.data.Dataset;
import smile.data.Instance;
import smile.data.Segment;
import smile.data.USPS;
import smile.feature.Standardizer;
import smile.io.Read;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.BinarySparseGaussianKernel;
import smile.util.SparseArray;
import smile.validation.Error;
import smile.validation.Validation;

/**
 *
 * @author Haifeng Li
 */
public class SVMTest {

    public SVMTest() {
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

    @Test(expected = Test.None.class)
    public void testSVMGuide1() throws IOException {
        System.out.println("svmguide1");

        MathEx.setSeed(19650218); // to get repeatable results.

        Dataset<Instance<SparseArray>> train = Read.libsvm(smile.util.Paths.getTestData("libsvm/svmguide1"));
        Dataset<Instance<SparseArray>> test  = Read.libsvm(smile.util.Paths.getTestData("libsvm/svmguide1.t"));

        int n = train.size();
        double[][] x = new double[n][4];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            Instance<SparseArray> sample = train.get(i);
            for (SparseArray.Entry e : sample.x()) {
                x[i][e.i] = e.x;
            }
            y[i] = sample.label() > 0 ? +1 : -1;
        }

        n = test.size();
        double[][] testx = new double[n][4];
        int[] testy = new int[n];
        for (int i = 0; i < n; i++) {
            Instance<SparseArray> sample = test.get(i);
            for (SparseArray.Entry e : sample.x()) {
                testx[i][e.i] = e.x;
            }
            testy[i] = sample.label() > 0 ? +1 : -1;
        }

        GaussianKernel kernel = new GaussianKernel(90);
        SVM<double[]> model = SVM.fit(x, y, kernel, 100, 1E-3);

        int[] prediction = Validation.test(model, testx);
        int error = Error.of(testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / testx.length);
        assertEquals(131, error);
    }

    @Test(expected = Test.None.class)
    public void testAdult() throws IOException {
        System.out.println("adult");

        MathEx.setSeed(19650218); // to get repeatable results.

        Dataset<Instance<SparseArray>> train = Read.libsvm(smile.util.Paths.getTestData("libsvm/data_lasvm_adult_adult.trn"));
        Dataset<Instance<SparseArray>> test  = Read.libsvm(smile.util.Paths.getTestData("libsvm/data_lasvm_adult_adult.tst"));

        int n = train.size();
        int[][] x = new int[n][];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            Instance<SparseArray> sample = train.get(i);
            x[i] = new int[sample.x().size()];
            int j = 0;
            for (SparseArray.Entry e : sample.x()) {
                x[i][j++] = e.i + 1; // The file is not standard libsvm format as the index starts with 0.
            }
            y[i] = sample.label();
        }

        n = test.size();
        int[][] testx = new int[n][];
        int[] testy = new int[n];
        for (int i = 0; i < n; i++) {
            Instance<SparseArray> sample = test.get(i);
            testx[i] = new int[sample.x().size()];
            int j = 0;
            for (SparseArray.Entry e : sample.x()) {
                testx[i][j++] = e.i + 1;
            }
            testy[i] = sample.label();
        }

        BinarySparseGaussianKernel kernel = new BinarySparseGaussianKernel(31.6);
        Classifier<int[]> model = SVM.fit(x, y, kernel, 100, 1E-3);

        int[] prediction = Validation.test(model, testx);
        int error = Error.of(testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / testx.length);
        assertEquals(2451, error);
    }

    @Test
    public void testSegment() {
        System.out.println("Segment");

        MathEx.setSeed(19650217); // to get repeatable results.

        Standardizer scaler = Standardizer.fit(Segment.x);
        double[][] x = scaler.transform(Segment.x);
        double[][] testx = scaler.transform(Segment.testx);

        GaussianKernel kernel = new GaussianKernel(6.4);
        OneVersusOne<double[]> model = OneVersusOne.fit(x, Segment.y, (xi, y) -> SVM.fit(xi, y, kernel, 100, 1E-3));

        int[] prediction = Validation.test(model, testx);
        int error = Error.of(Segment.testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / Segment.testx.length);
        assertEquals(33, error);
    }

    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        MathEx.setSeed(19650218); // to get repeatable results.

        GaussianKernel kernel = new GaussianKernel(8.0);
        OneVersusRest<double[]> model = OneVersusRest.fit(USPS.x, USPS.y, (x, y) -> SVM.fit(x, y, kernel, 5, 1E-3));

        int[] prediction = Validation.test(model, USPS.testx);
        int error = Error.of(USPS.testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / USPS.testx.length);
        assertEquals(87, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}
