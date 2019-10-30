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
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import smile.data.IndexNoun;
import smile.data.SwissRoll;
import smile.data.USPS;
import smile.math.distance.EditDistance;
import smile.math.distance.EuclideanDistance;
import smile.math.matrix.Matrix;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("rawtypes")
public class CoverTreeTest {

    public CoverTreeTest() {
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

        double[][] data = Matrix.randn(1000, 10).toArray();
        CoverTree<double[]> coverTree = new CoverTree<>(data, new EuclideanDistance());
        LinearSearch<double[]> naive = new LinearSearch<>(data, new EuclideanDistance());

        for (int i = 0; i < data.length; i++) {
            Neighbor n1 = coverTree.nearest(data[i]);
            Neighbor n2 = naive.nearest(data[i]);
            assertEquals(n1.index, n2.index);
            assertEquals(n1.value, n2.value);
            assertEquals(n1.distance, n2.distance, 1E-7);
        }
    }

    @Test
    public void testKnn() {
        System.out.println("knn");

        double[][] data = Matrix.randn(1000, 10).toArray();
        CoverTree<double[]> coverTree = new CoverTree<>(data, new EuclideanDistance());
        LinearSearch<double[]> naive = new LinearSearch<>(data, new EuclideanDistance());

        for (int i = 0; i < data.length; i++) {
            Neighbor[] n1 = coverTree.knn(data[i], 10);
            Neighbor[] n2 = naive.knn(data[i], 10);
            assertEquals(n1.length, n2.length);
            for (int j = 0; j < n1.length; j++) {
                assertEquals(n1[j].index, n2[j].index);
                assertEquals(n1[j].value, n2[j].value);
                assertEquals(n1[j].distance, n2[j].distance, 1E-7);
            }
        }
    }

    /**
     * Test of knn method when the data has only one elements
     */
    @Test
    public void testKnn1() {
        System.out.println("knn1");

        double[][] data = Matrix.randn(2, 10).toArray();
        double[][] data1 = {data[0]};
        EuclideanDistance d = new EuclideanDistance();
        CoverTree<double[]> coverTree = new CoverTree<>(data1, d);

        Neighbor[] n1 = coverTree.knn(data[1], 1);
        assertEquals(1, n1.length);
        assertEquals(0, n1[0].index);
        assertEquals(data[0], n1[0].value);
        assertEquals(d.d(data[0], data[1]), n1[0].distance, 1E-7);
    }

    @Test
    public void testRange() {
        System.out.println("range");

        double[][] data = Matrix.randn(1000, 10).toArray();
        CoverTree<double[]> coverTree = new CoverTree<>(data, new EuclideanDistance());
        LinearSearch<double[]> naive = new LinearSearch<>(data, new EuclideanDistance());

        List<Neighbor<double[], double[]>> n1 = new ArrayList<>();
        List<Neighbor<double[], double[]>> n2 = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            coverTree.range(data[i], 0.5, n1);
            naive.range(data[i], 0.5, n2);
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

    @Test(expected = Test.None.class)
    public void testSwissRoll() {
        System.out.println("----- Swiss Roll -----");

        double[][] x = new double[10000][];
        double[][] testx = new double[1000][];
        System.arraycopy(SwissRoll.data, 0, x, 0, x.length);
        System.arraycopy(SwissRoll.data, x.length, testx, 0, testx.length);

        long start = System.currentTimeMillis();
        CoverTree<double[]> coverTree = new CoverTree<>(x, new EuclideanDistance());
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building cover tree: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            coverTree.nearest(testx[i]);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            coverTree.knn(testx[i], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < testx.length; i++) {
            coverTree.range(testx[i], 8.0, n);
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

        long start = System.currentTimeMillis();
        CoverTree<double[]> coverTree = new CoverTree<>(x, new EuclideanDistance());
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building cover tree: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            coverTree.nearest(testx[i]);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (int i = 0; i < testx.length; i++) {
            coverTree.knn(testx[i], 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (int i = 0; i < testx.length; i++) {
            coverTree.range(testx[i], 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    @Test
    public void testStrings() {
        System.out.println("----- Strings -----");

        String[] words = IndexNoun.words;
        CoverTree<String> cover = new CoverTree<>(words, new EditDistance(50, true));

        long start = System.currentTimeMillis();
        List<Neighbor<String, String>> neighbors = new ArrayList<>();
        for (int i = 1000; i < 1100; i++) {
            cover.range(words[i], 1, neighbors);
            neighbors.clear();
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("String search: %.2fs%n", time);
    }
}