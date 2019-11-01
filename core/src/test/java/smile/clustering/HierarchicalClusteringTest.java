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
import smile.math.MathEx;
import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
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

        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
            
        HierarchicalClustering hc = HierarchicalClustering.fit(SingleLinkage.of(x));
        int[] label = hc.partition(10);
        double r = rand.measure(y, label);
        double r2 = ari.measure(y, label);
        System.out.format("SingleLinkage rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.1091, r, 1E-4);
        assertEquals(0.0000, r2, 1E-4);

        hc = HierarchicalClustering.fit(CompleteLinkage.of(x));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("CompleteLinkage rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8346, r, 1E-4);
        assertEquals(0.2930, r2, 1E-4);
            
        hc = HierarchicalClustering.fit(UPGMALinkage.of(x));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("UPGMA rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.2395, r, 1E-4);
        assertEquals(0.0068, r2, 1E-4);
            
        hc = HierarchicalClustering.fit(WPGMALinkage.of(x));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("WPGMA rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.7832, r, 1E-4);
        assertEquals(0.2561, r2, 1E-4);
            
        hc = HierarchicalClustering.fit(UPGMCLinkage.of(x));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("UPGMC rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.1093, r, 1E-4);
        assertEquals(0.0000, r2, 1E-4);
            
        hc = HierarchicalClustering.fit(WPGMCLinkage.of(x));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("WPGMC rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.1091, r, 1E-4);
        assertEquals(0.0000, r2, 1E-4);
            
        hc = HierarchicalClustering.fit(WardLinkage.of(x));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("Ward rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9254, r, 1E-4);
        assertEquals(0.6204, r2, 1E-4);
    }
}
