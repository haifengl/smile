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
import smile.validation.metric.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DBSCANTest {
    
    public DBSCANTest() {
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
    @Tag("integration")
    public void testGaussianMixture() throws Exception {
        System.out.println("Gaussian Mixture");
        GaussianMixture mixture = GaussianMixture.generate();
        double[][] x = mixture.x();
        int[] y = mixture.y();

        DBSCAN<double[]> model = DBSCAN.fit(x,200, 0.8);
        System.out.println(model);
        
        double r = RandIndex.of(y, model.group());
        double r2 = AdjustedRandIndex.of(y, model.group());
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8613, r, 1E-4);
        assertEquals(0.6301, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, model.group()));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, model.group()));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, model.group()));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, model.group()));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, model.group()));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, model.group()));

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    /** Two well-separated clusters of four points each. */
    private static final double[][] TWO_CLUSTERS = {
            {0.0, 0.0}, {0.0, 1.0}, {1.0, 0.0}, {1.0, 1.0},        // cluster A
            {10.0, 10.0}, {10.0, 11.0}, {11.0, 10.0}, {11.0, 11.0}  // cluster B
    };

    // ── Parameter validation ───────────────────────────────────────────────────

    @Test
    void givenZeroMinPts_whenFitting_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> DBSCAN.fit(TWO_CLUSTERS, 0, 1.5));
    }

    @Test
    void givenNegativeMinPts_whenFitting_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> DBSCAN.fit(TWO_CLUSTERS, -1, 1.5));
    }

    @Test
    void givenZeroRadius_whenFitting_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> DBSCAN.fit(TWO_CLUSTERS, 2, 0.0));
    }

    @Test
    void givenNegativeRadius_whenFitting_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> DBSCAN.fit(TWO_CLUSTERS, 2, -1.0));
    }

    // ── predict ───────────────────────────────────────────────────────────────

    @Test
    void givenTwoClusters_whenPredict_thenReturnsCorrectClusterLabels() {
        DBSCAN<double[]> model = DBSCAN.fit(TWO_CLUSTERS, 2, 2.0);

        assertEquals(2, model.k, "Should find exactly 2 clusters");

        int predA = model.predict(new double[]{0.5, 0.5});
        int predB = model.predict(new double[]{10.5, 10.5});

        assertNotEquals(Clustering.OUTLIER, predA, "Point inside cluster A should not be OUTLIER");
        assertNotEquals(Clustering.OUTLIER, predB, "Point inside cluster B should not be OUTLIER");
        assertNotEquals(predA, predB, "Points from different clusters should receive different labels");
    }

    @Test
    void givenPointFarFromAnyCorePoint_whenPredict_thenReturnsOutlier() {
        DBSCAN<double[]> model = DBSCAN.fit(TWO_CLUSTERS, 2, 2.0);

        int label = model.predict(new double[]{100.0, 100.0});
        assertEquals(Clustering.OUTLIER, label,
                "A point far from all core points should be labelled OUTLIER");
    }

    @Test
    void givenPredictPointNearTrainingCore_whenPredict_thenMatchesTrainingLabel() {
        DBSCAN<double[]> model = DBSCAN.fit(TWO_CLUSTERS, 2, 2.0);

        // The predicted label for a point near cluster A must equal the training label of cluster A.
        int trainingLabelA = model.group(0); // first training point is in cluster A
        int predictedLabelA = model.predict(new double[]{0.0, 0.5});

        assertEquals(trainingLabelA, predictedLabelA,
                "predict() should return the same label as the nearest core point");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void givenAllIsolatedPoints_whenFitting_thenAllLabeledOutlierAndKIsZero() {
        // With minPts=3 and radius=0.5, four isolated 1-D points are all noise.
        double[][] sparse = {{0.0}, {10.0}, {20.0}, {30.0}};
        DBSCAN<double[]> model = DBSCAN.fit(sparse, 3, 0.5);

        assertEquals(0, model.k, "No clusters should be found in fully sparse data");
        for (int i = 0; i < sparse.length; i++) {
            assertEquals(Clustering.OUTLIER, model.group(i),
                    "Every isolated point should be labelled OUTLIER");
        }
    }

    @Test
    void givenSingleDenseCluster_whenFitting_thenKIsOne() {
        // All points within a tight ball → single cluster, no outliers.
        double[][] dense = {{0.0}, {0.1}, {0.2}, {0.3}};
        DBSCAN<double[]> model = DBSCAN.fit(dense, 2, 0.5);

        assertEquals(1, model.k, "All points in one ball should form exactly one cluster");
        for (int i = 0; i < dense.length; i++) {
            assertNotEquals(Clustering.OUTLIER, model.group(i),
                    "No point should be noise when all are in one dense region");
        }
    }

    @Test
    void givenAccessors_whenQueried_thenReturnFittedParameters() {
        DBSCAN<double[]> model = DBSCAN.fit(TWO_CLUSTERS, 3, 2.0);
        assertEquals(3, model.minPoints());
        assertEquals(2.0, model.radius(), 1E-12);
    }
}
