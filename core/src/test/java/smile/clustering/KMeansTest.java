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
package smile.clustering;

import smile.io.Read;
import smile.io.Write;
import smile.datasets.GaussianMixture;
import smile.datasets.USPS;
import smile.math.MathEx;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class KMeansTest {
    GaussianMixture mixture = GaussianMixture.generate();
    double[][] x = mixture.x();
    int[] y = mixture.y();

    public KMeansTest() {

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

    @Test
    @Tag("integration")
    public void testBBD4() {
        System.out.println("BBD 4");
        var model = KMeans.fit(x, 4, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9459, r, 1E-4);
        assertEquals(0.8586, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));
    }

    @Test
    @Tag("integration")
    public void testLloyd4() {
        System.out.println("Lloyd 4");
        var model = KMeans.lloyd(x, 4, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9459, r, 1E-4);
        assertEquals(0.8586, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));
    }

    @Test
    @Tag("integration")
    public void testBBD64() {
        System.out.println("BBD 64");
        var model = KMeans.fit(x, 64, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.7550, r, 1E-4);
        assertEquals(0.0856, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));
    }

    @Test
    @Tag("integration")
    public void testLloyd64() {
        System.out.println("Lloyd 64");
        var model = KMeans.lloyd(x, 64, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.7550, r, 1E-4);
        assertEquals(0.0856, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));
    }

    @Test
    @Tag("integration")
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        double[][] x = usps.x();
        int[] y = usps.y();
        double[][] testx = usps.testx();
        int[] testy = usps.testy();

        var model = KMeans.fit(x, 10, 100);
        System.out.println(model);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9163, r, 1E-4);
        assertEquals(0.5590, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));

        int[] p = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            p[i] = model.predict(testx[i]);
        }
            
        r = RandIndex.of(testy, p);
        r2 = AdjustedRandIndex.of(testy, p);
        System.out.format("Testing rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9053, r, 1E-4);
        assertEquals(0.5027, r2, 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    @Tag("integration")
    public void givenFloatGaussianMixture_whenFittingKMeans_thenReturnFloatCentroidsWithAcceptableQuality() {
        System.out.println("BBD float 4");
        // Given – convert double[][] mixture to float[][]
        float[][] fx = new float[x.length][x[0].length];
        for (int i = 0; i < x.length; i++)
            for (int j = 0; j < x[0].length; j++)
                fx[i][j] = (float) x[i][j];

        // When
        var model = KMeans.fit(fx, 4, 100);
        System.out.println(model);

        // Then – return type must be CentroidClustering<float[], float[]>
        assertInstanceOf(float[].class, model.center(0));
        assertEquals(4, model.k());
        assertTrue(model.distortion() > 0.0);

        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Float training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        // float precision should give roughly the same cluster quality as double
        assertTrue(r > 0.90, "Rand index should exceed 0.90");
        assertTrue(r2 > 0.80, "Adjusted rand index should exceed 0.80");
    }

    @Test
    public void givenFloatData_whenFittingKMeans_thenTwoClustersCorrectlySeparated() {
        // Given – two well-separated float clusters
        float[][] data = {
            {0.0f, 0.0f}, {0.1f, 0.1f}, {0.0f, 0.2f},
            {5.0f, 5.0f}, {5.1f, 4.9f}, {4.9f, 5.1f}
        };

        // When
        var model = KMeans.fit(data, 2, 100);

        // Then
        assertEquals("K-Means", model.name());
        assertInstanceOf(float[].class, model.center(0));
        assertEquals(2, model.k());
        assertTrue(model.distortion() >= 0.0);
        // points within same cluster should share the same label
        assertEquals(model.group(0), model.group(1));
        assertEquals(model.group(0), model.group(2));
        assertEquals(model.group(3), model.group(4));
        assertEquals(model.group(3), model.group(5));
        // the two clusters must be distinct
        assertNotEquals(model.group(0), model.group(3));
    }

    @Test
    public void givenFloatData_whenFittingKMeans_thenCentroidDimensionMatchesInput() {
        // Given
        float[][] data = {
            {1.0f, 2.0f, 3.0f}, {1.1f, 2.1f, 2.9f},
            {9.0f, 8.0f, 7.0f}, {8.9f, 8.1f, 7.1f}
        };

        // When
        var model = KMeans.fit(data, 2, 100);

        // Then
        assertEquals(3, model.center(0).length);
        assertEquals(3, model.center(1).length);
    }

    @Test
    public void givenInvalidInput_whenFittingKMeans_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> KMeans.fit(new double[0][0], 2, 10));
        assertThrows(IllegalArgumentException.class, () -> KMeans.fit(new double[][] {{0.0}, {1.0, 2.0}}, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> KMeans.fit(new double[][] {{0.0}, {Double.NaN}}, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> KMeans.fit(new double[][] {{0.0}, {Double.POSITIVE_INFINITY}}, 2, 10));
    }

    @Test
    public void givenKGreaterThanSampleSize_whenFittingKMeans_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> KMeans.fit(new double[][] {{0.0}, {1.0}}, 3, 10));
    }

    @Test
    public void givenDataWithMissingValues_whenRunningLloyd_thenAllowNaNButRejectInfinity() {
        double[][] withNaN = {
                {0.0, Double.NaN},
                {0.1, 0.2},
                {5.0, 5.0},
                {5.1, 4.9}
        };
        assertDoesNotThrow(() -> KMeans.lloyd(withNaN, 2, 10));

        double[][] withInfinity = {
                {0.0, 1.0},
                {1.0, Double.POSITIVE_INFINITY},
                {2.0, 2.0}
        };
        assertThrows(IllegalArgumentException.class, () -> KMeans.lloyd(withInfinity, 2, 10));
    }

    @Test
    public void givenKMeansFit_whenModelReturned_thenUseKMeansName() {
        var model = KMeans.fit(new double[][] {{0.0}, {0.1}, {5.0}, {5.1}}, 2, 10);
        assertEquals("K-Means", model.name());
    }
}