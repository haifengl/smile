/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.neighbor;

import java.util.ArrayList;
import java.util.List;
import smile.math.MathEx;
import smile.math.distance.EditDistance;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.Matrix;
import smile.test.data.GaussianMixture;
import smile.test.data.IndexNoun;
import smile.test.data.SwissRoll;
import smile.test.data.USPS;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("rawtypes")
public class LinearSearchTest {

    public LinearSearchTest() {
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

    /**
     * Test of knn method when the data has only one elements
     */
    @Test
    public void testKnn1() {
        System.out.println("----- knn1 -----");

        double[][] data = Matrix.randn(2, 10).toArray();
        double[][] data1 = {data[0]};
        EuclideanDistance d = new EuclideanDistance();
        LinearSearch<double[], double[]> naive = LinearSearch.of(data1, d);

        Neighbor[] n1 = naive.search(data[1], 1);
        assertEquals(1, n1.length);
        assertEquals(0, n1[0].index);
        assertEquals(data[0], n1[0].value);
        assertEquals(d.d(data[0], data[1]), n1[0].distance, 1E-7);
    }

    @Test
    public void testSwissRoll() {
        System.out.println("----- Swiss Roll -----");

        double[][] x = new double[10000][];
        double[][] testx = new double[1000][];
        System.arraycopy(SwissRoll.data, 0, x, 0, x.length);
        System.arraycopy(SwissRoll.data, x.length, testx, 0, testx.length);

        LinearSearch<double[], double[]> naive = LinearSearch.of(x, new EuclideanDistance());

        long start = System.currentTimeMillis();
        for (double[] xi : testx) {
            naive.nearest(xi);
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (double[] xi : testx) {
            naive.search(xi, 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (double[] xi : testx) {
            naive.search(xi, 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    @Test
    public void testUSPS() {
        System.out.println("----- USPS -----");

        double[][] x = USPS.x;
        double[][] testx = USPS.testx;

        LinearSearch<double[], double[]> naive = LinearSearch.of(x, new EuclideanDistance());

        long start = System.currentTimeMillis();
        for (double[] xi : testx) {
            naive.nearest(xi);
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (double[] xi : testx) {
            naive.search(xi, 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (double[] xi : testx) {
            naive.search(xi, 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    @Test
    public void testStrings() {
        System.out.println("----- Strings -----");

        String[] words = IndexNoun.words;
        LinearSearch<String, String> naive = LinearSearch.of(words, new EditDistance(true));

        long start = System.currentTimeMillis();
        List<Neighbor<String, String>> neighbors = new ArrayList<>();
        for (int i = 1000; i < 1100; i++) {
            naive.search(words[i], 1, neighbors);
            neighbors.clear();
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("String search: %.2fs%n", time);
    }

    @Test
    public void testGaussianMixture() {
        System.out.println("----- Gaussian Mixture -----");

        double[][] data = GaussianMixture.x;
        LinearSearch<double[], double[]> naive = LinearSearch.of(data, new EuclideanDistance());

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            naive.nearest(data[MathEx.randomInt(data.length)]);
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            naive.search(data[MathEx.randomInt(data.length)], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            naive.search(data[MathEx.randomInt(data.length)], 1.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }
}