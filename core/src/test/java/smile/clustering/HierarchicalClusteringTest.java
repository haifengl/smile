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

import smile.clustering.linkage.SingleLinkage;
import smile.clustering.linkage.WPGMCLinkage;
import smile.clustering.linkage.WardLinkage;
import smile.clustering.linkage.UPGMCLinkage;
import smile.clustering.linkage.WPGMALinkage;
import smile.clustering.linkage.UPGMALinkage;
import smile.clustering.linkage.CompleteLinkage;
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

        int n = x.length;
        double[][] proximity = new double[n][];
        for (int i = 0; i < n; i++) {
            proximity[i] = new double[i + 1];
            for (int j = 0; j < i; j++) {
                proximity[i][j] = MathEx.distance(x[i], x[j]);
            }
        }

        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
            
        HierarchicalClustering hc = new HierarchicalClustering(new SingleLinkage(proximity));
        int[] label = hc.partition(10);
        double r = rand.measure(y, label);
        double r2 = ari.measure(y, label);
        System.out.format("SingleLinkage rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.1);
            
        hc = new HierarchicalClustering(new CompleteLinkage(proximity));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("CompleteLinkage rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.75);
            
        hc = new HierarchicalClustering(new UPGMALinkage(proximity));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("UPGMA rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.1);
            
        hc = new HierarchicalClustering(new WPGMALinkage(proximity));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("WPGMA rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.2);
            
        hc = new HierarchicalClustering(new UPGMCLinkage(proximity));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("UPGMC rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.1);
            
        hc = new HierarchicalClustering(new WPGMCLinkage(proximity));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("WPGMC rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.1);
            
        hc = new HierarchicalClustering(new WardLinkage(proximity));
        label = hc.partition(10);
        r = rand.measure(y, label);
        r2 = ari.measure(y, label);
        System.out.format("Ward rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.9);
        assertTrue(r2 > 0.5);
    }
}
