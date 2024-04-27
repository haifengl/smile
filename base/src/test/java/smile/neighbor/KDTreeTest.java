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
import java.util.Collections;
import java.util.List;
import smile.math.MathEx;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.Matrix;
import smile.test.data.GaussianMixture;
import smile.test.data.USPS;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class KDTreeTest {

    public KDTreeTest() {

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
    public void testNearest() {
        System.out.println("nearest");

        double[][] data = Matrix.randn(1000, 10).toArray();
        KDTree<double[]> kdtree = new KDTree<>(data, data);
        LinearSearch<double[], double[]> naive = LinearSearch.of(data, new EuclideanDistance());

        for (int i = 0; i < data.length; i++) {
            Neighbor<double[], double[]> n1 = naive.nearest(data[i]);
            Neighbor<double[], double[]> n2 = kdtree.nearest(data[i]);
            assertEquals(n1.index, n2.index);
            assertEquals(n1.value, n2.value);
            assertEquals(n1.distance, n2.distance, 1E-7);
        }
    }

    @Test
    public void testKnn() {
        System.out.println("knn");

        double[][] data = Matrix.randn(1000, 10).toArray();
        KDTree<double[]> kdtree = new KDTree<>(data, data);
        LinearSearch<double[], double[]> naive = LinearSearch.of(data, new EuclideanDistance());

        for (int i = 0; i < data.length; i++) {
            Neighbor<double[], double[]> [] n1 = naive.search(data[i], 10);
            Neighbor<double[], double[]> [] n2 = kdtree.search(data[i], 10);
            for (int j = 0; j < n1.length; j++) {
                assertEquals(n1[j].index, n2[j].index);
                assertEquals(n1[j].value, n2[j].value);
                assertEquals(n1[j].distance, n2[j].distance, 1E-7);
            }
        }
    }

    @Test
    public void testRange() {
        System.out.println("range 0.5");

        double[][] data = Matrix.randn(1000, 10).toArray();
        KDTree<double[]> kdtree = new KDTree<>(data, data);
        LinearSearch<double[], double[]> naive = LinearSearch.of(data, new EuclideanDistance());

        List<Neighbor<double[], double[]>> n1 = new ArrayList<>();
        List<Neighbor<double[], double[]>> n2 = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            kdtree.search(data[i], 0.5, n1);
            naive.search(data[i], 0.5, n2);
            Collections.sort(n1);
            Collections.sort(n2);
            assertEquals(n1.size(), n2.size());
            for (int j = 0; j < n1.size(); j++) {
                assertEquals(n1.get(j).index, n2.get(j).index);
                assertEquals(n1.get(j).value, n2.get(j).value);
                assertEquals(n1.get(j).distance, n2.get(j).distance, 1E-7);
            }
            n1.clear();
            n2.clear();
        }

        System.out.println("range 1.5");
        for (int i = 0; i < data.length; i++) {
            naive.search(data[i], 1.5, n1);
            kdtree.search(data[i], 1.5, n2);
            Collections.sort(n1);
            Collections.sort(n2);
            assertEquals(n1.size(), n2.size());
            for (int j = 0; j < n1.size(); j++) {
                assertEquals(n1.get(j).index, n2.get(j).index);
                assertEquals(n1.get(j).value, n2.get(j).value);
                assertEquals(n1.get(j).distance, n2.get(j).distance, 1E-7);
            }
            n1.clear();
            n2.clear();
        }
    }

    @Test
    public void testGaussianMixture() {
        System.out.println("----- Gaussian Mixture -----");

        double[][] data = GaussianMixture.x;

        long start = System.currentTimeMillis();
        KDTree<double[]> kdtree = new KDTree<>(data, data);
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building KD-tree: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            kdtree.nearest(data[MathEx.randomInt(data.length)]);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            kdtree.search(data[MathEx.randomInt(data.length)], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            kdtree.search(data[MathEx.randomInt(data.length)], 1.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    @Test
    public void testBenchmark() throws Exception {
        System.out.println("----- Benchmark -----");

        int N = 40000;
        int scale = 100;
        int numTests = 100_000;

        double[][] coords = new double[N][3];
        for (int i = 0; i < N; i++) {
            coords[i][0] = MathEx.random() * scale;
            coords[i][1] = MathEx.random() * scale;
            coords[i][2] = MathEx.random() * scale;
        }
        KDTree<double[]> kdt = new KDTree<>(coords, coords);

        long start = System.currentTimeMillis();
        double[] q = new double[3];
        for (int i = 0; i < numTests; i++) {
            q[0] = MathEx.random() * scale;
            q[1] = MathEx.random() * scale;
            q[2] = MathEx.random() * scale;
            kdt.nearest(q);
        }

        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Benchmark: %.2fs%n", time);
        assertTrue(time < 0.25);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("----- USPS -----");

        double[][] x = USPS.x;
        double[][] testx = USPS.testx;

        long start = System.currentTimeMillis();
        KDTree<double[]> kdtree = new KDTree<>(x, x);
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building KD-tree: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            kdtree.nearest(testx[i]);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            kdtree.search(testx[i], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < testx.length; i++) {
            kdtree.search(testx[i], 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }
}
