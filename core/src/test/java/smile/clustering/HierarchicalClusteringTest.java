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

import smile.clustering.linkage.*;
import smile.io.Read;
import smile.io.Write;
import smile.datasets.USPS;
import smile.validation.metric.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class HierarchicalClusteringTest {
    /**
     * Four 2-D points forming two tight pairs separated by a large gap:
     *   pair A: (0,0), (0.1,0.1)  — intra-pair distance ≈ 0.14
     *   pair B: (10,10), (10.1,10.1) — intra-pair distance ≈ 0.14
     *   inter-pair distance ≈ 14.1
     */
    private static final double[][] DATA = {
            {0.0, 0.0}, {0.1, 0.1},
            {10.0, 10.0}, {10.1, 10.1}
    };

    private HierarchicalClustering model;

    public HierarchicalClusteringTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    void setUp() {
        model = HierarchicalClustering.fit(CompleteLinkage.of(DATA));
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
        int[] y = usps.y();

        HierarchicalClustering model = HierarchicalClustering.fit(SingleLinkage.of(x));
        int[] label = model.partition(10);
        double r = RandIndex.of(y, label);
        double r2 = AdjustedRandIndex.of(y, label);
        System.out.format("SingleLinkage rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.1091, r, 1E-4);
        assertEquals(0.0000, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, label));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, label));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, label));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, label));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, label));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, label));

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);

        model = HierarchicalClustering.fit(CompleteLinkage.of(x));
        label = model.partition(10);
        r = RandIndex.of(y, label);
        r2 = AdjustedRandIndex.of(y, label);
        System.out.format("CompleteLinkage rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8346, r, 1E-4);
        assertEquals(0.2930, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, label));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, label));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, label));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, label));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, label));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, label));

        model = HierarchicalClustering.fit(UPGMALinkage.of(x));
        label = model.partition(10);
        r = RandIndex.of(y, label);
        r2 = AdjustedRandIndex.of(y, label);
        System.out.format("UPGMA rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.2395, r, 1E-4);
        assertEquals(0.0068, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, label));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, label));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, label));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, label));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, label));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, label));

        model = HierarchicalClustering.fit(WPGMALinkage.of(x));
        label = model.partition(10);
        r = RandIndex.of(y, label);
        r2 = AdjustedRandIndex.of(y, label);
        System.out.format("WPGMA rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.7832, r, 1E-4);
        assertEquals(0.2561, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, label));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, label));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, label));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, label));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, label));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, label));

        model = HierarchicalClustering.fit(UPGMCLinkage.of(x));
        label = model.partition(10);
        r = RandIndex.of(y, label);
        r2 = AdjustedRandIndex.of(y, label);
        System.out.format("UPGMC rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.1093, r, 1E-4);
        assertEquals(0.0000, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, label));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, label));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, label));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, label));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, label));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, label));

        model = HierarchicalClustering.fit(WPGMCLinkage.of(x));
        label = model.partition(10);
        r = RandIndex.of(y, label);
        r2 = AdjustedRandIndex.of(y, label);
        System.out.format("WPGMC rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.1091, r, 1E-4);
        assertEquals(0.0000, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, label));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, label));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, label));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, label));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, label));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, label));

        model = HierarchicalClustering.fit(WardLinkage.of(x));
        label = model.partition(10);
        r = RandIndex.of(y, label);
        r2 = AdjustedRandIndex.of(y, label);
        System.out.format("Ward rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9254, r, 1E-4);
        assertEquals(0.6204, r2, 1E-4);

        System.out.format("MI = %.2f%n", MutualInformation.of(y, label));
        System.out.format("NMI.joint = %.2f%%%n", 100 * NormalizedMutualInformation.joint(y, label));
        System.out.format("NMI.max = %.2f%%%n", 100 * NormalizedMutualInformation.max(y, label));
        System.out.format("NMI.min = %.2f%%%n", 100 * NormalizedMutualInformation.min(y, label));
        System.out.format("NMI.sum = %.2f%%%n", 100 * NormalizedMutualInformation.sum(y, label));
        System.out.format("NMI.sqrt = %.2f%%%n", 100 * NormalizedMutualInformation.sqrt(y, label));
    }

    // ── partition(int k) ──────────────────────────────────────────────────────

    @Test
    void givenFourPointsTwoClusters_whenPartitionByK_thenNearbyPointsShareLabel() {
        int[] labels = model.partition(2);

        assertEquals(4, labels.length);
        // Points within the same tight pair must share a label.
        assertEquals(labels[0], labels[1], "Points (0,0) and (0.1,0.1) should be co-clustered");
        assertEquals(labels[2], labels[3], "Points (10,10) and (10.1,10.1) should be co-clustered");
        // The two pairs must be in different clusters.
        assertNotEquals(labels[0], labels[2], "The two tight pairs should be in different clusters");
    }

    @Test
    void givenFourPoints_whenPartitionByK4_thenEachPointIsItsOwnCluster() {
        int[] labels = model.partition(4);

        assertEquals(4, labels.length);
        // All four labels should be distinct.
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                assertNotEquals(labels[i], labels[j],
                        "With k=n each point should be its own cluster");
            }
        }
    }

    // ── partition(double h) ───────────────────────────────────────────────────

    @Test
    void givenHeightBetweenIntraAndInterClusterDistance_whenPartitionByH_thenTwoClusters() {
        // The intra-pair merge happens at ≈ 0.14; the inter-pair merge at ≈ 14.1.
        // Cutting at h = 5.0 should yield exactly 2 clusters.
        int[] labels = model.partition(5.0);

        assertEquals(labels[0], labels[1], "Low-height cut should keep tight pair A together");
        assertEquals(labels[2], labels[3], "Low-height cut should keep tight pair B together");
        assertNotEquals(labels[0], labels[2], "Low-height cut should separate the two pairs");
    }

    @Test
    void givenHeightBelowAllMerges_whenPartitionByH_thenEachPointAlone() {
        // Cutting at h = 0.01 (below any merge) gives one cluster per point.
        int[] labels = model.partition(0.01);

        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                assertNotEquals(labels[i], labels[j],
                        "Cutting below any merge should leave every point alone");
            }
        }
    }

    @Test
    void givenHeightAboveAllMerges_whenPartitionByH_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> model.partition(1000.0),
                "A cut height above all merges should throw IllegalArgumentException");
    }

    // ── Linkage consistency ───────────────────────────────────────────────────

    @Test
    void givenSingleLinkage_whenPartitionByK_thenResultIsValid() {
        HierarchicalClustering single = HierarchicalClustering.fit(SingleLinkage.of(DATA));
        int[] labels = single.partition(2);

        assertEquals(4, labels.length);
        // With single-linkage on clearly separated data the two pairs should still be separated.
        assertEquals(labels[0], labels[1]);
        assertEquals(labels[2], labels[3]);
        assertNotEquals(labels[0], labels[2]);
    }

    @Test
    void givenWardLinkage_whenHeightIsMonotonic_thenNoExceptionDuringPartition() {
        // Ward stores squared distances, which HierarchicalClustering square-roots.
        // partition(double h) validates monotonicity — should not throw for Ward.
        HierarchicalClustering ward = HierarchicalClustering.fit(WardLinkage.of(DATA));
        assertDoesNotThrow(() -> ward.partition(5.0));
    }
}
