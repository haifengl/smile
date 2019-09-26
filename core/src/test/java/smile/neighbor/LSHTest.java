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

package smile.neighbor;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.USPS;
import smile.math.distance.EuclideanDistance;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("rawtypes")
public class LSHTest {
    double[][] x = USPS.x;
    double[][] testx = USPS.testx;
    LSH<double[]> lsh = new LSH<>(x, x);
    LinearSearch<double[]> naive = new LinearSearch<>(x, new EuclideanDistance());

    public LSHTest() throws IOException {
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
     * Test of nearest method, of class LSH.
     */
    @Test
    public void testNearest() {
        System.out.println("nearest");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        double dist = 0.0;
        int hit = 0;
        for (int i = 0; i < testx.length; i++) {
            Neighbor neighbor = lsh.nearest(testx[i]);
            if (neighbor.index != -1) {
                dist += neighbor.distance;
                hit++;
            }
            if (neighbor.index == naive.nearest(testx[i]).index) {
                recall++;
            }
        }

        recall /= testx.length;
        System.out.println("recall is " + recall);
        System.out.println("average distance is " + dist / hit);
        System.out.println("time is " + (System.currentTimeMillis() - time) / 1000.0);
    }

    /**
     * Test of knn method, of class LSH.
     */
    @Test
    public void testKnn() {
        System.out.println("knn");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        for (int i = 0; i < testx.length; i++) {
            int k = 3;
            Neighbor[] n1 = lsh.knn(testx[i], k);
            Neighbor[] n2 = naive.knn(testx[i], k);
            int hit = 0;
            for (int m = 0; m < n1.length && n1[m] != null; m++) {
                for (int n = 0; n < n2.length && n2[n] != null; n++) {
                    if (n1[m].index == n2[n].index) {
                        hit++;
                        break;
                    }
                }
            }
            recall += 1.0 * hit / k;
        }

        recall /= testx.length;
        System.out.println("recall is " + recall);
        System.out.println("time is " + (System.currentTimeMillis() - time) / 1000.0);
    }

    /**
     * Test of range method, of class LSH.
     */
    @Test
    public void testRange() {
        System.out.println("range");

        long time = System.currentTimeMillis();
        double recall = 0.0;
        for (int i = 0; i < testx.length; i++) {
            ArrayList<Neighbor<double[], double[]>> n1 = new ArrayList<>();
            ArrayList<Neighbor<double[], double[]>> n2 = new ArrayList<>();
            lsh.range(testx[i], 8.0, n1);
            naive.range(testx[i], 8.0, n2);

            int hit = 0;
            for (int m = 0; m < n1.size(); m++) {
                for (int n = 0; n < n2.size(); n++) {
                    if (n1.get(m).index == n2.get(n).index) {
                        hit++;
                        break;
                    }
                }
            }
            if (!n2.isEmpty()) {
                recall += 1.0 * hit / n2.size();
            }
        }

        recall /= testx.length;
        System.out.println("recall is " + recall);
        System.out.println("time is " + (System.currentTimeMillis() - time) / 1000.0);
    }
}
