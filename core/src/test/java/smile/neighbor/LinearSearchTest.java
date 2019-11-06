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

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.data.GaussianMixture;
import smile.data.IndexNoun;
import smile.data.SwissRoll;
import smile.data.USPS;
import smile.math.MathEx;
import smile.math.distance.EditDistance;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.Matrix;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("rawtypes")
public class LinearSearchTest {

    public LinearSearchTest() {
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
     * Test of knn method when the data has only one elements
     */
    @Test
    public void testKnn1() {
        System.out.println("----- knn1 -----");

        double[][] data = Matrix.randn(2, 10).toArray();
        double[][] data1 = {data[0]};
        EuclideanDistance d = new EuclideanDistance();
        LinearSearch<double[]> naive = new LinearSearch<>(data1, d);

        Neighbor[] n1 = naive.knn(data[1], 1);
        assertEquals(1, n1.length);
        assertEquals(0, n1[0].index);
        assertEquals(data[0], n1[0].value);
        assertEquals(d.d(data[0], data[1]), n1[0].distance, 1E-7);
    }

    @Test(expected = Test.None.class)
    public void testSwissRoll() {
        System.out.println("----- Swiss Roll -----");

        double[][] x = new double[10000][];
        double[][] testx = new double[1000][];
        System.arraycopy(SwissRoll.data, 0, x, 0, x.length);
        System.arraycopy(SwissRoll.data, x.length, testx, 0, testx.length);

        LinearSearch<double[]> naive = new LinearSearch<>(x, new EuclideanDistance());

        long start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            naive.nearest(testx[i]);
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            naive.knn(testx[i], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < testx.length; i++) {
            naive.range(testx[i], 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    @Test(expected = Test.None.class)
    public void testUSPS() {
        System.out.println("----- USPS -----");

        double[][] x = USPS.x;
        double[][] testx = USPS.testx;

        LinearSearch<double[]> naive = new LinearSearch<>(x, new EuclideanDistance());

        long start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            naive.nearest(testx[i]);
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            naive.knn(testx[i], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < testx.length; i++) {
            naive.range(testx[i], 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    @Test
    public void testStrings() {
        System.out.println("----- Strings -----");

        String[] words = IndexNoun.words;
        LinearSearch<String> naive = new LinearSearch<>(words, new EditDistance(true));

        long start = System.currentTimeMillis();
        List<Neighbor<String, String>> neighbors = new ArrayList<>();
        for (int i = 1000; i < 1100; i++) {
            naive.range(words[i], 1, neighbors);
            neighbors.clear();
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("String search: %.2fs%n", time);
    }

    @Test
    public void testGaussianMixture() {
        System.out.println("----- Gaussian Mixture -----");

        double[][] data = GaussianMixture.x;
        LinearSearch<double[]> naive = new LinearSearch<>(data, new EuclideanDistance());

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            naive.nearest(data[MathEx.randomInt(data.length)]);
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            naive.knn(data[MathEx.randomInt(data.length)], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            naive.range(data[MathEx.randomInt(data.length)], 1.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }
}