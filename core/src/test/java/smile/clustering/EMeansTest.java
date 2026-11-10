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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.*;
import smile.datasets.GaussianMixture;
import smile.datasets.USPS;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.util.AlgoStatus;
import smile.util.IterativeAlgorithmController;
import smile.validation.metric.AdjustedRandIndex;
import smile.validation.metric.RandIndex;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for {@link EMeans}.
 *
 * @author Haifeng Li
 */
public class EMeansTest {
    static GaussianMixture mixture = GaussianMixture.generate();
    static double[][] x = mixture.x();
    static int[] y = mixture.y();

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void givenScalingLaws_whenResolvingRecipe_thenMatchesReferenceFormulas() {
        // Given / When / Then: power-of-two rounding
        assertEquals(1, EMeans.roundPowerOfTwo(0.5));
        assertEquals(8, EMeans.roundPowerOfTwo(6.0));
        assertEquals(16, EMeans.roundPowerOfTwo(12.0));
        assertEquals(1024, EMeans.roundPowerOfTwo(1000.0));

        // BatchLaw.AUTO selects QUALITY for d <= 16 and THROUGHPUT for d > 16
        assertEquals(EMeans.BatchLaw.QUALITY, EMeans.resolveBatchLaw(EMeans.BatchLaw.AUTO, 2));
        assertEquals(EMeans.BatchLaw.QUALITY, EMeans.resolveBatchLaw(EMeans.BatchLaw.AUTO, 16));
        assertEquals(EMeans.BatchLaw.THROUGHPUT, EMeans.resolveBatchLaw(EMeans.BatchLaw.AUTO, 17));
        assertEquals(EMeans.BatchLaw.THROUGHPUT, EMeans.resolveBatchLaw(EMeans.BatchLaw.THROUGHPUT, 2));

        // Default unusedThreshold is 0.0 for d <= 2 and 0.1 for d > 2
        assertEquals(0.0, EMeans.resolveUnusedThreshold(Double.NaN, 2), 1E-12);
        assertEquals(0.1, EMeans.resolveUnusedThreshold(Double.NaN, 3), 1E-12);
        assertEquals(0.25, EMeans.resolveUnusedThreshold(0.25, 2), 1E-12);

        // QUALITY batch size is ~1/8 of THROUGHPUT batch size
        int bThroughput = EMeans.resolveBatchSize(100_000, 256, 64, 5.0, EMeans.BatchLaw.THROUGHPUT, 0);
        int bQuality = EMeans.resolveBatchSize(100_000, 256, 64, 5.0, EMeans.BatchLaw.QUALITY, 0);
        assertEquals(bThroughput, bQuality * 8);

        // Step law alpha0 is in [1E-4, 1.0] and increases with batchSize
        double alphaSmall = EMeans.resolveAlpha0(100_000, 256, 5.0, bQuality, 0.0);
        double alphaLarge = EMeans.resolveAlpha0(100_000, 256, 5.0, bThroughput, 0.0);
        assertTrue(alphaSmall >= 1E-4 && alphaSmall <= 1.0);
        assertTrue(alphaLarge > alphaSmall && alphaLarge <= 1.0);

        // Cosine schedule starts at alpha0 and decays to 0.1 * alpha0
        assertEquals(0.5, EMeans.scheduledAlpha(0.5, 0, 100), 1E-12);
        assertEquals(0.05, EMeans.scheduledAlpha(0.5, 100, 100), 1E-12);
    }

    @Test
    public void givenEpochBudget_whenComputingEpochRows_thenHandlesWholeAndFractionalPasses() {
        // Given / When / Then: integer passes
        assertArrayEquals(new int[]{1000, 1000, 1000}, EMeans.epochRows(1000, 3.0));

        // Fractional passes > 1: whole-1 full passes, fractional pass, closing full pass
        assertArrayEquals(new int[]{1000, 500, 1000}, EMeans.epochRows(1000, 2.5));

        // Fractional pass < 1: single partial pass
        assertArrayEquals(new int[]{400}, EMeans.epochRows(1000, 0.4));
    }

    @Test
    public void givenDeadClusters_whenReviving_thenSplitsDonorsAndHalvesUsage() {
        // Given: 3 clusters where cluster 2 is dead (usage = 0) and cluster 0 is dominant
        double[][] centroids = {
                {10.0, 20.0, 30.0},
                {1.0, 2.0, 3.0},
                {0.0, 0.0, 0.0}
        };
        double[] usage = {100.0, 50.0, 0.0};

        // When
        int splits = EMeans.reviveDeadClusters(centroids, usage, 0.1);

        // Then
        assertEquals(1, splits);
        assertTrue(usage[2] > 0.0, "Revived cluster must receive half of donor's mass");
        assertEquals(150.0, usage[0] + usage[1] + usage[2], 1E-9, "Total mass must be conserved");
        assertFalse(java.util.Arrays.equals(centroids[0], centroids[2]),
                "Donor and child centroids must be separated");
    }

    @Test
    public void givenZeroDonorCentroid_whenReviving_thenSeparatesIdenticalCoordinates() {
        // Given: donor centroid at origin (all zeros) and one dead cluster
        double[][] centroids = {
                {0.0, 0.0},
                {5.0, 5.0}
        };
        double[] usage = {10.0, 0.0};

        // When
        int splits = EMeans.reviveDeadClusters(centroids, usage, 0.1);

        // Then
        assertEquals(1, splits);
        assertNotEquals(centroids[0][0], centroids[1][0],
                "Zero donor and child must be separated by nextUp");
    }

    @Test
    public void givenOptions_whenRoundTrippedThroughProperties_thenAllValuesPreserved() {
        // Given
        EMeans.Options opts = new EMeans.Options(
                8, 3.5, EMeans.BatchLaw.QUALITY, 128, 0.25, 0.15, true, null);

        // When
        Properties props = opts.toProperties();
        EMeans.Options restored = EMeans.Options.of(props);

        // Then
        assertEquals(8, restored.k());
        assertEquals(3.5, restored.epochs(), 1E-12);
        assertEquals(EMeans.BatchLaw.QUALITY, restored.batchLaw());
        assertEquals(128, restored.batchSize());
        assertEquals(0.25, restored.alpha0(), 1E-12);
        assertEquals(0.15, restored.unusedThreshold(), 1E-12);
        assertTrue(restored.spherical());
    }

    @Test
    public void givenInvalidOptions_whenConstructingOptions_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new EMeans.Options(1));
        assertThrows(IllegalArgumentException.class, () -> new EMeans.Options(2, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EMeans.Options(2, -1.0));
        assertThrows(IllegalArgumentException.class, () -> new EMeans.Options(2, Double.NaN));
        assertThrows(NullPointerException.class, () -> new EMeans.Options(2, 5.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EMeans.Options(2, 5.0, EMeans.BatchLaw.AUTO, -1, 0.0, Double.NaN, false, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EMeans.Options(2, 5.0, EMeans.BatchLaw.AUTO, 0, 1.5, Double.NaN, false, null));
        assertThrows(IllegalArgumentException.class,
                () -> new EMeans.Options(2, 5.0, EMeans.BatchLaw.AUTO, 0, 0.0, -0.1, false, null));
    }

    @Test
    public void givenDoubleAndFloatData_whenFittingEMeans_thenTwoClustersCorrectlySeparated() {
        // Given
        double[][] dData = {
                {0.0, 0.0}, {0.1, 0.1}, {0.0, 0.2}, {0.2, 0.0},
                {5.0, 5.0}, {5.1, 4.9}, {4.9, 5.1}, {5.0, 5.2}
        };
        float[][] fData = {
                {0.0f, 0.0f}, {0.1f, 0.1f}, {0.0f, 0.2f}, {0.2f, 0.0f},
                {5.0f, 5.0f}, {5.1f, 4.9f}, {4.9f, 5.1f}, {5.0f, 5.2f}
        };

        // When
        var dModel = EMeans.fit(dData, 2, 5.0);
        MathEx.setSeed(19650218);
        var fModel = EMeans.fit(fData, 2, 5.0);

        // Then
        assertEquals("E-Means", dModel.name());
        assertEquals(2, dModel.k());
        assertEquals(dModel.group(0), dModel.group(1));
        assertEquals(dModel.group(0), dModel.group(2));
        assertEquals(dModel.group(4), dModel.group(5));
        assertNotEquals(dModel.group(0), dModel.group(4));

        assertEquals("E-Means", fModel.name());
        assertInstanceOf(float[].class, fModel.center(0));
        assertEquals(2, fModel.k());
        assertEquals(fModel.group(0), fModel.group(1));
        assertEquals(fModel.group(4), fModel.group(5));
        assertNotEquals(fModel.group(0), fModel.group(4));
    }

    @Test
    public void givenFractionalAndSubEpochBudgets_whenFittingEMeans_thenAllPointsAssigned() {
        // Given
        double[][] data = new double[40][3];
        for (int i = 0; i < 20; i++) {
            data[i] = new double[]{0.01 * i, 0.01 * i, 0.01 * i};
            data[20 + i] = new double[]{10.0 + 0.01 * i, 10.0 + 0.01 * i, 10.0 + 0.01 * i};
        }

        // When: 2.5 epochs and 0.5 epochs
        var model25 = EMeans.fit(data, 2, 2.5);
        var model05 = EMeans.fit(data, 2, 0.5);

        // Then
        assertEquals(40, model25.group().length);
        assertEquals(40, model05.group().length);
        for (int i = 0; i < 40; i++) {
            assertTrue(model25.group(i) >= 0 && model25.group(i) < 2);
            assertTrue(model05.group(i) >= 0 && model05.group(i) < 2);
        }
        assertTrue(Double.isFinite(model25.distortion()));
        assertTrue(Double.isFinite(model05.distortion()));
    }

    @Test
    public void givenNormalizedDirectionalData_whenFittingSphericalEMeans_thenUnitCentroidsReturned() throws Exception {
        // Given: two directional clusters on the unit sphere in R^3
        double[][] dData = {
                {1.0, 0.0, 0.0}, {0.98, 0.198997, 0.0}, {0.98, 0.0, 0.198997},
                {0.0, 1.0, 0.0}, {0.198997, 0.98, 0.0}, {0.0, 0.98, 0.198997}
        };
        float[][] fData = new float[dData.length][3];
        for (int i = 0; i < dData.length; i++) {
            for (int j = 0; j < 3; j++) {
                fData[i][j] = (float) dData[i][j];
            }
        }

        var opts = new EMeans.Options(2, 5.0, EMeans.BatchLaw.AUTO, 0, 0.0, 0.0, true, null);

        // When
        var dModel = EMeans.fit(dData, opts);
        var fModel = EMeans.fit(fData, opts);

        // Then: centroids have unit norm and separate the two directions
        for (int j = 0; j < 2; j++) {
            assertEquals(1.0, MathEx.norm(dModel.center(j)), 1E-6);
        }
        assertEquals(dModel.group(0), dModel.group(1));
        assertEquals(dModel.group(3), dModel.group(4));
        assertNotEquals(dModel.group(0), dModel.group(3));
        assertEquals(dModel.group(0), dModel.predict(new double[]{1.0, 0.0, 0.0}));
        assertEquals(dModel.group(3), dModel.predict(new double[]{0.0, 1.0, 0.0}));

        assertEquals(fModel.group(0), fModel.predict(new float[]{1.0f, 0.0f, 0.0f}));
        assertEquals(fModel.group(3), fModel.predict(new float[]{0.0f, 1.0f, 0.0f}));

        // Verify spherical model is serializable
        Path path = Write.object(dModel);
        try {
            @SuppressWarnings("unchecked")
            var loaded = (CentroidClustering<double[], double[]>) Read.object(path);
            assertEquals(dModel.predict(new double[]{1.0, 0.0, 0.0}),
                    loaded.predict(new double[]{1.0, 0.0, 0.0}));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void givenController_whenInterruptedDuringTraining_thenStopsEarlyWithValidClustering() {
        // Given
        double[][] data = new double[50][4];
        for (int i = 0; i < 25; i++) {
            data[i] = new double[]{0.0, 0.0, 0.0, 0.0};
            data[25 + i] = new double[]{5.0, 5.0, 5.0, 5.0};
        }
        try (var controller = new IterativeAlgorithmController<AlgoStatus>()) {
            var opts = new Clustering.Options(2, 10, 1E-4, controller);
            controller.stop();

            // When
            var model = EMeans.fit(data, opts);
            var fData = new float[][]{{0.0f, 0.0f}, {0.1f, 0.1f}, {5.0f, 5.0f}, {5.1f, 5.1f}};
            var fModel = EMeans.fit(fData, opts);

            // Then
            assertEquals(2, model.k());
            assertTrue(Double.isFinite(model.distortion()));
            assertEquals(2, fModel.k());
            assertTrue(Double.isFinite(fModel.distortion()));
        }
    }

    @Test
    public void givenInvalidInput_whenFittingEMeans_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new double[0][0], 2));
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new double[][]{{0.0}, {1.0, 2.0}}, 2));
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new double[][]{{0.0}, {Double.NaN}}, 2));
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new double[][]{{0.0}, {Double.POSITIVE_INFINITY}}, 2));
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new double[][]{{0.0}, {1.0}}, 3));

        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new float[0][0], 2));
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new float[][]{{0.0f}, {1.0f, 2.0f}}, 2));
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new float[][]{{0.0f}, {Float.NaN}}, 2));
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new float[][]{{0.0f}, {Float.POSITIVE_INFINITY}}, 2));
        assertThrows(IllegalArgumentException.class, () -> EMeans.fit(new float[][]{{0.0f}, {1.0f}}, 3));
    }

    @Test
    @Tag("integration")
    public void givenGaussianMixture_whenFittingEMeans_thenAchievesHighRandIndex() {
        // Given / When: double precision
        var model = EMeans.fit(x, 4, 5.0);
        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());

        // Then
        assertTrue(r > 0.90, "Rand index should exceed 0.90, got " + r);
        assertTrue(r2 > 0.80, "Adjusted rand index should exceed 0.80, got " + r2);

        // Given / When: single precision
        float[][] fx = new float[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[0].length; j++) {
                fx[i][j] = (float) x[i][j];
            }
        }
        MathEx.setSeed(19650218);
        var fModel = EMeans.fit(fx, 4, 5.0);
        double fr = RandIndex.of(y, fModel.group());
        double fr2 = AdjustedRandIndex.of(y, fModel.group());
        assertTrue(fr > 0.90, "Float Rand index should exceed 0.90, got " + fr);
        assertTrue(fr2 > 0.80, "Float Adjusted rand index should exceed 0.80, got " + fr2);
    }

    @Test
    @Tag("integration")
    public void givenUSPS_whenFittingEMeans_thenClustersAndSerializes() throws Exception {
        // Given
        var usps = new USPS();
        double[][] trainX = usps.x();
        int[] trainY = usps.y();
        double[][] testX = usps.testx();
        int[] testY = usps.testy();

        // When: 5 epochs of E-Means
        var model = EMeans.fit(trainX, 10, 5.0);

        // Then
        double r = RandIndex.of(trainY, model.group());
        double r2 = AdjustedRandIndex.of(trainY, model.group());
        assertTrue(r > 0.88, "USPS training Rand index should exceed 0.88, got " + r);
        assertTrue(r2 > 0.45, "USPS training Adjusted Rand index should exceed 0.45, got " + r2);

        int[] p = new int[testX.length];
        for (int i = 0; i < testX.length; i++) {
            p[i] = model.predict(testX[i]);
        }
        double testR = RandIndex.of(testY, p);
        assertTrue(testR > 0.87, "USPS testing Rand index should exceed 0.87, got " + testR);

        Path temp = Write.object(model);
        try {
            Read.object(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
