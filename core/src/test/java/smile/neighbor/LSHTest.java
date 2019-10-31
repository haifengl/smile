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
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.USPS;
import smile.math.MathEx;
import smile.math.distance.EuclideanDistance;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("rawtypes")
public class LSHTest {
    double[][] x = USPS.x;
    double[][] testx = USPS.testx;
    LSH<double[]> lsh;
    LinearSearch<double[]> naive = new LinearSearch<>(x, new EuclideanDistance());

    public LSHTest() throws IOException {
        MathEx.setSeed(19650218); // to get repeatable results.
        lsh = new LSH<>(x, x, 4.0, 1017881);
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
    public void testNearest() {
        System.out.println("nearest");

        int recall = 0;
        double error = 0.0;
        int hit = 0;
        for (int i = 0; i < testx.length; i++) {
            Neighbor neighbor = lsh.nearest(testx[i]);
            if (neighbor != null) {
                hit++;

                Neighbor truth = naive.nearest(testx[i]);
                if (neighbor.index == truth.index) {
                    recall++;
                } else {
                    error += Math.abs(neighbor.distance - truth.distance) / truth.distance;
                }
            }
        }

        error /= (hit - recall);

        assertEquals(1154, recall);
        assertEquals(2007, hit);
        assertEquals(0.1305, error, 1E-4);
        System.out.format("recall is %.2f%%%n", 100.0 * recall / testx.length);
        System.out.format("error when miss is %.2f%%%n", 100.0 * error);
        System.out.format("null rate is %.2f%%%n", 100.0 - 100.0 * hit / testx.length);
    }

    @Test
    public void testKnn() {
        System.out.println("knn");

        int[] recall = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            int k = 7;
            Neighbor[] n1 = lsh.knn(testx[i], k);
            Neighbor[] n2 = naive.knn(testx[i], k);
            for (Neighbor m2 : n2) {
                for (Neighbor m1 : n1) {
                    if (m1.index == m2.index) {
                        recall[i]++;
                        break;
                    }
                }
            }
        }

        System.out.format("q1     of recall is %d%n", MathEx.q1(recall));
        System.out.format("median of recall is %d%n", MathEx.median(recall));
        System.out.format("q3     of recall is %d%n", MathEx.q3(recall));
    }

    @Test
    public void testRange() {
        System.out.println("range");

        int[] recall = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            ArrayList<Neighbor<double[], double[]>> n1 = new ArrayList<>();
            ArrayList<Neighbor<double[], double[]>> n2 = new ArrayList<>();
            lsh.range(testx[i], 8.0, n1);
            naive.range(testx[i], 8.0, n2);

            for (Neighbor m2 : n2) {
                for (Neighbor m1 : n1) {
                    if (m1.index == m2.index) {
                        recall[i]++;
                        break;
                    }
                }
            }
        }

        System.out.format("q1     of recall is %d%n", MathEx.q1(recall));
        System.out.format("median of recall is %d%n", MathEx.median(recall));
        System.out.format("q3     of recall is %d%n", MathEx.q3(recall));
    }

    @Test(expected = Test.None.class)
    public void testSpeed() throws Exception {
        System.out.println("Speed");

        long start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            lsh.nearest(testx[i]);
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            lsh.knn(testx[i], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < testx.length; i++) {
            lsh.range(testx[i], 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }
}
