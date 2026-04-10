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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

import java.util.ArrayList;
import java.util.List;
import smile.math.MathEx;
import smile.datasets.USPS;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("rawtypes")
public class MPLSHTest {

    public MPLSHTest() throws Exception {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() throws Exception {
        System.out.println("MPLSH training...");

        // Training takes a long time.
        // So we combine all tests into one function to avoid run it multiple times.
        MathEx.setSeed(19650218); // to get repeatable results.
        var usps = new USPS();
        double[][] x = usps.x();
        double[][] testx = usps.testx();

        LinearSearch<double[], double[]> naive = LinearSearch.of(x, MathEx::distance);
        MPLSH<double[]> lsh = new MPLSH<>(256, 100, 3, 4.0);
        for (double[] xi : x) {
            lsh.put(xi, xi);
        }

        double[][] train = new double[500][];
        int[] index = MathEx.permutate(x.length);
        for (int i = 0; i < train.length; i++) {
            train[i] = x[index[i]];
        }
        lsh.fit(naive, train, 8.0);

        System.out.println("===== nearest =====");
        int match = 0;
        double error = 0.0;
        int hit = 0;
        for (double[] xi : testx) {
            Neighbor neighbor = lsh.nearest(xi);
            if (neighbor != null) {
                hit++;

                Neighbor truth = naive.nearest(xi);
                if (neighbor.index() == truth.index()) {
                    match++;
                } else {
                    error += Math.abs(neighbor.distance() - truth.distance()) / truth.distance();
                }
            }
        }

        error /= (hit - match);

        assertEquals(1754, match);
        assertEquals(2007, hit);
        assertEquals(0.0716, error, 1E-4);
        System.out.format("recall is %.2f%%%n", 100.0 * match / testx.length);
        System.out.format("error when miss is %.2f%%%n", 100.0 * error);
        System.out.format("null rate is %.2f%%%n", 100.0 - 100.0 * hit / testx.length);

        System.out.println("===== knn =====");
        int[] recall = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            int k = 7;
            Neighbor[] n1 = lsh.search(testx[i], k, 0.95, 50);
            Neighbor[] n2 = naive.search(testx[i], k);
            for (Neighbor m2 : n2) {
                for (Neighbor m1 : n1) {
                    if (m1.index() == m2.index()) {
                        recall[i]++;
                        break;
                    }
                }
            }
        }

        System.out.format("q1     of recall is %d%n", MathEx.q1(recall));
        System.out.format("median of recall is %d%n", MathEx.median(recall));
        System.out.format("q3     of recall is %d%n", MathEx.q3(recall));

        System.out.println("===== range =====");
        recall = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            ArrayList<Neighbor<double[], double[]>> n1 = new ArrayList<>();
            ArrayList<Neighbor<double[], double[]>> n2 = new ArrayList<>();
            lsh.search(testx[i], 8.0, n1, 0.95, 50);
            naive.search(testx[i], 8.0, n2);

            for (Neighbor m2 : n2) {
                for (Neighbor m1 : n1) {
                    if (m1.index() == m2.index()) {
                        recall[i]++;
                        break;
                    }
                }
            }
        }

        System.out.format("q1     of recall is %d%n", MathEx.q1(recall));
        System.out.format("median of recall is %d%n", MathEx.median(recall));
        System.out.format("q3     of recall is %d%n", MathEx.q3(recall));

        System.out.println("===== speed =====");
        long start = System.currentTimeMillis();
        for (double[] xi : testx) {
            lsh.nearest(xi);
        }
        double time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        for (double[] xi : testx) {
            lsh.search(xi, 10);
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("10-NN: %.2fs%n", time);

        start = System.currentTimeMillis();
        List<Neighbor<double[], double[]>> n = new ArrayList<>();
        for (double[] xi : testx) {
            lsh.search(xi, 8.0, n);
            n.clear();
        }
        time = (System.currentTimeMillis() - start) / 1000.0;
        System.out.format("Range: %.2fs%n", time);
    }
}
