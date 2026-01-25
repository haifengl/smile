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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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
    
    public HierarchicalClusteringTest() {
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
}
