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
import java.util.List;
import smile.math.MathEx;
import smile.math.distance.EditDistance;
import smile.datasets.GaussianMixture;
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

        double[][] data = MathEx.randn(2, 10);
        double[][] data1 = {data[0]};
        LinearSearch<double[], double[]> naive = LinearSearch.of(data1, MathEx::distance);

        Neighbor[] n1 = naive.search(data[1], 1);
        assertEquals(1, n1.length);
        assertEquals(0, n1[0].index());
        assertEquals(data[0], n1[0].value());
        assertEquals(MathEx.distance(data[0], data[1]), n1[0].distance(), 1E-7);
    }

    @Test
    public void testSwissRoll() throws Exception {
        System.out.println("----- Swiss Roll -----");
        var roll = new SwissRoll();
        double[][] x = new double[10000][];
        double[][] testx = new double[1000][];
        System.arraycopy(roll.data(), 0, x, 0, x.length);
        System.arraycopy(roll.data(), x.length, testx, 0, testx.length);

        LinearSearch<double[], double[]> naive = LinearSearch.of(x, MathEx::distance);

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
    public void testUSPS() throws Exception {
        System.out.println("----- USPS -----");
        var usps = new USPS();
        double[][] x = usps.x();
        double[][] testx = usps.testx();

        LinearSearch<double[], double[]> naive = LinearSearch.of(x, MathEx::distance);

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
    public void testStrings() throws Exception {
        System.out.println("----- Strings -----");
        var wordnet = new WordNet();
        String[] words = wordnet.words();
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
        GaussianMixture mixture = GaussianMixture.generate();
        double[][] data = mixture.x();
        LinearSearch<double[], double[]> naive = LinearSearch.of(data, MathEx::distance);

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