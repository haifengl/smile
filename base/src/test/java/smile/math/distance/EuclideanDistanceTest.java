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
package smile.math.distance;

import smile.util.SparseArray;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class EuclideanDistanceTest {

    public EuclideanDistanceTest() {
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
     * Test of distance method, of class EuclideanDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");

        EuclideanDistance distance = new EuclideanDistance();
        double[] x = {1.0, 2.0, 3.0, 4.0};
        double[] y = {4.0, 3.0, 2.0, 1.0};
        assertEquals(4.472136, distance.d(x, y), 1E-6);

        double[] w = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] v = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        assertEquals(2.422302, distance.d(w, v), 1E-6);

        SparseArray s = new SparseArray();
        s.append(1, 1.0);
        s.append(2, 2.0);
        s.append(3, 3.0);
        s.append(4, 4.0);

        SparseArray t = new SparseArray();
        t.append(1, 4.0);
        t.append(2, 3.0);
        t.append(3, 2.0);
        t.append(4, 1.0);

        assertEquals(4.472136, new SparseEuclideanDistance().d(s, t), 1E-6);

        s = new SparseArray();
        s.append(2, 2.0);
        s.append(3, 3.0);
        s.append(4, 4.0);

        t = new SparseArray();
        t.append(1, 4.0);
        t.append(2, 3.0);
        t.append(3, 2.0);

        assertEquals(5.830951, new SparseEuclideanDistance().d(s, t), 1E-6);

        s = new SparseArray();
        s.append(1, 1.0);

        t = new SparseArray();
        t.append(3, 2.0);

        assertEquals(2.236067, new SparseEuclideanDistance().d(s, t), 1E-6);
    }

    @Test
    public void testDistanceIntArray() {
        System.out.println("distance int array");
        int[] x = {1, 2, 3, 4};
        int[] y = {4, 3, 2, 1};
        assertEquals(4.472136, new EuclideanDistance().d(x, y), 1E-6);
    }

    @Test
    public void testDistanceSameVector() {
        System.out.println("distance same vector");
        double[] x = {1.0, 2.0, 3.0};
        assertEquals(0.0, new EuclideanDistance().d(x, x), 1E-9);
    }

    @Test
    public void testDistanceWithNaN() {
        System.out.println("distance with NaN");
        // x[0] and y[0] are NaN -> excluded; only indices 1,2 contribute
        double[] x = {Double.NaN, 3.0, 4.0};
        double[] y = {1.0, Double.NaN, 1.0};
        // Only index 2: d=3, n=3, m=1; dist = sqrt(3 * 9 / 1) = sqrt(27)
        assertEquals(Math.sqrt(27.0), new EuclideanDistance().d(x, y), 1E-9);
    }

    @Test
    public void testDistanceAllNaN() {
        System.out.println("distance all NaN");
        double[] x = {Double.NaN, Double.NaN};
        double[] y = {Double.NaN, Double.NaN};
        assertTrue(Double.isNaN(new EuclideanDistance().d(x, y)));
    }

    @Test
    public void testWeightedDistance() {
        System.out.println("weighted distance");
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {4.0, 5.0, 6.0};
        double[] weight = {1.0, 2.0, 3.0};
        EuclideanDistance dist = new EuclideanDistance(weight);
        // sqrt(1*(3^2) + 2*(3^2) + 3*(3^2)) = sqrt(9+18+27) = sqrt(54)
        assertEquals(Math.sqrt(54.0), dist.d(x, y), 1E-9);
    }

    @Test
    public void testDistancePdist() {
        System.out.println("pdist");
        EuclideanDistance dist = new EuclideanDistance();
        double[][] data = {{0.0, 0.0}, {3.0, 0.0}, {0.0, 4.0}};
        double[][] x = data;
        var D = dist.pdist(x);
        assertEquals(3.0, D.get(1, 0), 1E-9);
        assertEquals(4.0, D.get(2, 0), 1E-9);
        assertEquals(5.0, D.get(2, 1), 1E-9);
        assertEquals(0.0, D.get(0, 0), 1E-9);
    }
}

