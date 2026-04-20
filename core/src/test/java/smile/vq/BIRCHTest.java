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
package smile.vq;

import smile.datasets.USPS;
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class BIRCHTest {
    
    public BIRCHTest() {
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
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        double[][] x = usps.x();
        double[][] testx = usps.testx();

        BIRCH model = new BIRCH(x[0].length, 5, 5, 6.0);
        for (double[] xi : x) {
            model.update(xi);
        }

        double error = 0.0;
        for (double[] xi : x) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= x.length;
        System.out.format("Training Quantization Error = %.4f%n", error);
        assertEquals(5.9017, error, 1E-4);

        error = 0.0;
        for (double[] xi : testx) {
            double[] yi = model.quantize(xi);
            error += MathEx.distance(xi, yi);
        }
        error /= testx.length;

        System.out.format("Test Quantization Error = %.4f%n", error);
        assertEquals(7.3498, error, 1E-4);
    }

    @Test
    void givenInvalidBirchParameters_whenConstructing_thenThrowsIllegalArgumentException() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> new BIRCH(0, 5, 5, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BIRCH(2, 1, 5, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BIRCH(2, 5, 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BIRCH(2, 5, 5, 0.0));
    }

    @Test
    void givenEmptyBirchModel_whenQuantizeOrCentroids_thenThrowsIllegalStateException() {
        // Given
        BIRCH birch = new BIRCH(2, 5, 5, 1.0);

        // When / Then
        assertThrows(IllegalStateException.class, () -> birch.quantize(new double[] {0.0, 0.0}));
        assertThrows(IllegalStateException.class, birch::centroids);
    }

    @Test
    void givenSimpleBirchModel_whenGettingCentroids_thenReturnsLeafCentroids() {
        // Given
        BIRCH birch = new BIRCH(2, 3, 3, 0.5);
        birch.update(new double[] {0.0, 0.0});
        birch.update(new double[] {0.1, 0.1});
        birch.update(new double[] {5.0, 5.0});

        // When
        double[][] centroids = birch.centroids();

        // Then
        assertEquals(2, centroids.length);
    }

    @Test
    void givenBirchModel_whenInputDimensionMismatch_thenThrowsIllegalArgumentException() {
        // Given
        BIRCH birch = new BIRCH(2, 3, 3, 0.5);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> birch.update(new double[] {1.0}));

        birch.update(new double[] {0.0, 0.0});
        assertThrows(IllegalArgumentException.class, () -> birch.quantize(new double[] {1.0}));
    }

    @Test
    void givenSingleCluster_whenQuantizing_thenReturnsCentroid() {
        // Given: all points within radius T, so they form one CF
        BIRCH birch = new BIRCH(1, 5, 5, 10.0);
        birch.update(new double[]{0.0});
        birch.update(new double[]{2.0});
        birch.update(new double[]{4.0});

        // When
        double[] result = birch.quantize(new double[]{2.0});

        // Then: the centroid of the single sub-cluster is (0+2+4)/3 = 2
        assertEquals(2.0, result[0], 1E-12);
    }

    @Test
    void givenTwoDistinctClusters_whenQuantizing_thenSnapsToNearestCentroid() {
        // Given: two clusters far apart with T small enough to keep them separate
        BIRCH birch = new BIRCH(1, 5, 5, 0.5);
        birch.update(new double[]{0.0});
        birch.update(new double[]{0.1});
        birch.update(new double[]{100.0});
        birch.update(new double[]{100.1});

        // When
        double[] nearZero = birch.quantize(new double[]{0.05});
        double[] nearHundred = birch.quantize(new double[]{100.05});

        // Then
        assertTrue(nearZero[0] < 1.0, "Should snap to the cluster near 0");
        assertTrue(nearHundred[0] > 99.0, "Should snap to the cluster near 100");
    }

    @Test
    void givenLeafAtCapacity_whenPointForcesNewCF_thenLeafSplitsAndMoreCentroidsAvailable() {
        // Given: L=2 means a leaf holds at most 2 CFs; T=0.1 means close points merge
        // Three distinct sub-clusters: near 0, near 1, near 5 — they each need their own CF.
        BIRCH birch = new BIRCH(1, 5, 2, 0.1);
        birch.update(new double[]{0.0});
        birch.update(new double[]{0.05}); // merges into CF(~0)
        birch.update(new double[]{1.0});  // new CF — leaf now has 2 CFs (k=2=L)
        birch.update(new double[]{5.0});  // new CF needed, leaf full → leaf splits

        // When
        double[][] centroids = birch.centroids();

        // Then: at least 3 sub-cluster centroids exist after the split
        assertTrue(centroids.length >= 3,
                "Leaf split should produce at least 3 sub-cluster centroids");
    }

    @Test
    void givenManyPoints_whenInternalNodeFills_thenTreeGrowsWithoutError() {
        // Given: B=2 (very small branching) forces internal node splits quickly
        BIRCH birch = new BIRCH(1, 2, 2, 0.01);

        // When: insert 20 well-separated points to force multiple splits at every level
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 20; i++) {
                birch.update(new double[]{i * 1.0});
            }
        });

        // Then: all points are reachable and centroids are available
        double[][] centroids = birch.centroids();
        assertTrue(centroids.length >= 10,
                "Many distinct points should produce many sub-cluster centroids");
    }

    @Test
    void givenIdenticalPoints_whenQuantizing_thenCentroidEqualsPoint() {
        // Given
        BIRCH birch = new BIRCH(2, 5, 5, 1.0);
        double[] point = {3.0, 4.0};
        for (int i = 0; i < 5; i++) {
            birch.update(point);
        }

        // When
        double[] result = birch.quantize(point);

        // Then: centroid of identical points equals the point itself
        assertArrayEquals(point, result, 1E-12);
    }
}
