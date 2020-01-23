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

package smile.clustering;

import smile.clustering.linkage.*;
import smile.data.USPS;
import smile.validation.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class HierarchicalClusteringTest {
    
    public HierarchicalClusteringTest() {
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


    /**
     * Test of learn method, of class GMeans.
     */
    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        double[][] x = USPS.x;
        int[] y = USPS.y;

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

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);

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
