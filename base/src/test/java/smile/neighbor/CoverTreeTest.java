/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import smile.math.MathEx;
import smile.math.distance.EditDistance;
import smile.datasets.WordNet;
import smile.datasets.SwissRoll;
import smile.datasets.USPS;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("rawtypes")
public class CoverTreeTest {

    public CoverTreeTest() {
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

        double[][] data = MathEx.randn(1000, 10);
        CoverTree<double[], double[]> coverTree = CoverTree.of(data, MathEx::distance);
        LinearSearch<double[], double[]> naive = LinearSearch.of(data, MathEx::distance);

        for (double[] datum : data) {
            Neighbor n1 = coverTree.nearest(datum);
            Neighbor n2 = naive.nearest(datum);
            assertEquals(n1.index(), n2.index());
            assertEquals(n1.value(), n2.value());
            assertEquals(n1.distance(), n2.distance(), 1E-7);
        }
    }

    @Test
    public void testKnn() {
        System.out.println("knn");

        double[][] data = MathEx.randn(1000, 10);
        CoverTree<double[], double[]> coverTree = CoverTree.of(data, MathEx::distance);
        LinearSearch<double[], double[]> naive = LinearSearch.of(data, MathEx::distance);

        for (double[] datum : data) {
            Neighbor[] n1 = coverTree.search(datum, 10);
            Neighbor[] n2 = naive.search(datum, 10);
            assertEquals(n1.length, n2.length);
            for (int j = 0; j < n1.length; j++) {
                assertEquals(n1[j].index(), n2[j].index());
                assertEquals(n1[j].value(), n2[j].value());
                assertEquals(n1[j].distance(), n2[j].distance(), 1E-7);
            }
        }
    }

    /**
     * Test of knn method when the data has only one element.
     */
    @Test
    public void testKnn1() {
        System.out.println("knn1");

        double[][] data = MathEx.randn(2, 10);
        double[][] data1 = {data[0]};
        CoverTree<double[], double[]> coverTree = CoverTree.of(data1, MathEx::distance);

        Neighbor[] n1 = coverTree.search(data[1], 1);
        assertEquals(1, n1.length);
        assertEquals(0, n1[0].index());
        assertEquals(data[0], n1[0].value());
        assertEquals(MathEx.distance(data[0], data[1]), n1[0].distance(), 1E-7);
    }

    @Test
    public void testRange() {
        System.out.println("range");

        double[][] data = MathEx.randn(1000, 10);
        CoverTree<double[], double[]> coverTree = CoverTree.of(data, MathEx::distance);
        LinearSearch<double[], double[]> naive = LinearSearch.of(data, MathEx::distance);

        List<Neighbor<double[], double[]>> n1 = new ArrayList<>();
        List<Neighbor<double[], double[]>> n2 = new ArrayList<>();
        for (double[] datum : data) {
            coverTree.search(datum, 0.5, n1);
            naive.search(datum, 0.5, n2);
            Collections.sort(n1);
            Collections.sort(n2);
            assertEquals(n1.size(), n2.size());
            for (int j = 0; j < n1.size(); j++) {
                assertEquals(n1.get(j).index(), n2.get(j).index());
                assertEquals(n1.get(j).value(), n2.get(j).value());
                assertEquals(n1.get(j).distance(), n2.get(j).distance(), 1E-7);
            }
            n1.clear();
            n2.clear();
        }
    }

    @Test
    public void testSwissRoll() throws Exception {
        System.out.println("----- Swiss Roll -----");
        var roll = new SwissRoll();
        double[][] x = new double[10000][];
        double[][] testx = new double[1000][];
        System.arraycopy(roll.data(), 0, x, 0, x.length);
        System.arraycopy(roll.data(), x.length, testx, 0, testx.length);

        long start = System.currentTimeMillis();
        CoverTree<double[], double[]> coverTree = CoverTree.of(x, MathEx::distance);
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building cover tree: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (double[] xi : testx) {
            coverTree.nearest(xi);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (double[] xi : testx) {
            coverTree.search(xi, 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (double[] xi : testx) {
            coverTree.search(xi, 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("----- USPS -----");
        var usps = new USPS();
        double[][] x = Arrays.copyOf(usps.x(), 2000);
        double[][] testx = usps.testx();

        long start = System.currentTimeMillis();
        CoverTree<double[], double[]> coverTree = CoverTree.of(x, MathEx::distance);
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Building cover tree: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (double[] xi : testx) {
            coverTree.nearest(xi);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (double[] xi : testx) {
            coverTree.search(xi, 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> list = new ArrayList<>();
        for (double[] xi : testx) {
            coverTree.search(xi, 8.0, list);
            list.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }

    @Test
    public void testStrings() throws Exception {
        System.out.println("----- Strings -----");
        var wordnet = new WordNet();
        String[] words = Arrays.copyOf(wordnet.words(), 10000);
        CoverTree<String, String> cover = CoverTree.of(words, new EditDistance(50, true));

        long start = System.currentTimeMillis();
        List<Neighbor<String, String>> neighbors = new ArrayList<>();
        for (int i = 1000; i < 1100; i++) {
            cover.search(words[i], 3, neighbors);
            neighbors.clear();
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("String search: %.2fs%n", time);
    }
}
