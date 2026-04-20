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

import java.io.IOException;
import java.util.Properties;
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
 * Tests for smile.classification.SVM.
 *
 * @author Haifeng Li
 */
public class SVMTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void givenSvmguide1_whenFittingWithGaussianKernel_thenErrorWithinBound() throws IOException {
        // Given
        SparseDataset<Integer> train = Read.libsvm(smile.io.Paths.getTestData("libsvm/svmguide1.dat"));
        SparseDataset<Integer> test  = Read.libsvm(smile.io.Paths.getTestData("libsvm/svmguide1.t.dat"));

        int n = train.size();
        double[][] x = new double[n][4];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            SampleInstance<SparseArray, Integer> sample = train.get(i);
            for (SparseArray.Entry e : sample.x()) x[i][e.index()] = e.value();
            y[i] = sample.y() > 0 ? +1 : -1;
        }
        n = test.size();
        double[][] testx = new double[n][4];
        int[] testy = new int[n];
        for (int i = 0; i < n; i++) {
            SampleInstance<SparseArray, Integer> sample = test.get(i);
            for (SparseArray.Entry e : sample.x()) testx[i][e.index()] = e.value();
            testy[i] = sample.y() > 0 ? +1 : -1;
        }

        // When
        GaussianKernel kernel = new GaussianKernel(90);
        SVM<double[]> model = SVM.fit(x, y, kernel, new SVM.Options(100));
        int[] prediction = model.predict(testx);

        // Then
        int error = Error.of(testy, prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / testx.length);
        assertEquals(130, error, 10);
    }

    @Test
    public void givenSegmentData_whenOvoWithGaussianKernel_thenErrorWithinBound() throws Exception {
        // Given
        var segment = new ImageSegmentation();
        InvertibleColumnTransform scaler = Standardizer.fit(segment.train());
        double[][] x    = scaler.apply(segment.formula().x(segment.train())).toArray();
        double[][] testx = scaler.apply(segment.formula().x(segment.test())).toArray();

        // When
        GaussianKernel kernel = new GaussianKernel(6.4);
        var options = new SVM.Options(100);
        OneVersusOne<double[]> model = OneVersusOne.fit(x, segment.y(), (xi, yi) -> SVM.fit(xi, yi, kernel, options));
        int[] prediction = model.predict(testx);

        // Then
        int error = Error.of(segment.testy(), prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / segment.testx().length);
        assertEquals(33, error, 5);
    }

    @Test
    public void givenSegmentData_whenOvrWithLinearKernel_thenModelFits() throws Exception {
        // Given
        var segment = new ImageSegmentation();
        InvertibleColumnTransform scaler = Standardizer.fit(segment.train());
        double[][] x    = scaler.apply(segment.formula().x(segment.train())).toArray();
        double[][] testx = scaler.apply(segment.formula().x(segment.test())).toArray();

        // When
        var options = new SVM.Options(100);
        OneVersusRest<double[]> model = OneVersusRest.fit(x, segment.y(), (xi, yi) -> SVM.fit(xi, yi, options));
        int[] prediction = model.predict(testx);

        // Then
        assertEquals(segment.testy().length, prediction.length);
    }

    @Test
    public void givenOptions_whenRoundTripViaProperties_thenValuesPreserved() {
        // Given
        var opts = new SVM.Options(5.0, 1E-4, 2);

        // When
        Properties props  = opts.toProperties();
        var        loaded = SVM.Options.of(props);

        // Then
        assertEquals(opts, loaded);
    }

    @Test
    public void givenNegativeC_whenConstructingOptions_thenThrowsWithCorrectMessage() {
        // When
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> new SVM.Options(-0.5, 1E-3, 1));

        // Then — error must NOT say "iterations"; must say "penalty parameter"
        assertTrue(ex.getMessage().toLowerCase().contains("penalty"),
                "Error message should mention 'penalty' but was: " + ex.getMessage());
    }

    @Test
    public void givenInvalidTol_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SVM.Options(1.0, 0.0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SVM.Options(1.0, -1E-3, 1));
    }

    @Test
    public void givenInvalidEpochs_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new SVM.Options(1.0, 1E-3, 0));
    }

    @Test
    @Tag("integration")
    public void givenUSPS_whenFittingOvrWithGaussianKernel_thenErrorWithinBound() throws Exception {
        // Given
        var usps = new USPS();
        GaussianKernel kernel = new GaussianKernel(8.0);
        var options = new SVM.Options(5);

        // When
        OneVersusRest<double[]> model = OneVersusRest.fit(usps.x(), usps.y(),
                (x, y) -> SVM.fit(x, y, kernel, options));
        int[] prediction = model.predict(usps.testx());

        // Then
        int error = Error.of(usps.testy(), prediction);
        System.out.format("Test Error = %d, Accuracy = %.2f%%%n", error, 100.0 - 100.0 * error / usps.testx().length);
        assertEquals(86, error, 5);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}
