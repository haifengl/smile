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

import smile.data.GaussianMixture;
import smile.math.MathEx;
import smile.neighbor.KDTree;
import smile.stat.distribution.MultivariateGaussianDistribution;
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
 * @author Haifeng Li
 */
public class DBSCANTest {
    
    public DBSCANTest() {
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

    @Test
    public void testGaussianMixture() {
        System.out.println("Gaussian Mixture");

        double[][] data = GaussianMixture.data;
        int[] label = GaussianMixture.label;

        DBSCAN<double[]> dbscan = DBSCAN.fit(data, new KDTree<>(data, data), 200, 0.8);
        System.out.println(dbscan);
        
        int[] size = dbscan.size;
        int n = 0;
        for (int i = 0; i < size.length-1; i++) {
            n += size[i];
        }
        
        int[] y1 = new int[n];
        int[] y2 = new int[n];
        for (int i = 0, j = 0; i < data.length; i++) {
            if (dbscan.y[i] != Clustering.OUTLIER) {
                y1[j] = label[i];                
                y2[j++] = dbscan.y[i];
            }
        }
        
        AdjustedRandIndex ari = new AdjustedRandIndex();
        RandIndex rand = new RandIndex();
        double r = rand.measure(y1, y2);
        double r2 = ari.measure(y1, y2);
        System.out.println("The number of clusters: " + dbscan.k);
        System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertTrue(r > 0.40);
        assertTrue(r2 > 0.15);
    }
}
