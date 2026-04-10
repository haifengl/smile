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
public class ManhattanDistanceTest {

    public ManhattanDistanceTest() {
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
     * Test of distance method, of class ManhattanDistance.
     */
    @Test
    public void testDistanceInt() {
        System.out.println("distance int");
        int[] x = {1, 2, 3, 4};
        int[] y = {4, 3, 2, 1};
        assertEquals(8, new ManhattanDistance().d(x, y), 1E-6);
    }

    /**
     * Test of distance method, of class ManhattanDistance.
     */
    @Test
    public void testDistanceDouble() {
        System.out.println("distance double");
        double[] x = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] y = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        assertEquals(4.485227, new ManhattanDistance().d(x, y), 1E-6);
    }

    @Test
    public void testDistanceSameVector() {
        System.out.println("distance same vector");
        double[] x = {1.0, 2.0, 3.0};
        assertEquals(0.0, new ManhattanDistance().d(x, x), 1E-9);
    }

    @Test
    public void testDistanceWithNaN() {
        System.out.println("distance with NaN");
        // index 0: (NaN, 1.0) -> skip; index 1: (2.0, NaN) -> skip; index 2: (5.0, 1.0) -> |5-1|=4
        // n=3, m=1; dist = 3 * 4 / 1 = 12
        double[] x = {Double.NaN, 2.0, 5.0};
        double[] y = {1.0, Double.NaN, 1.0};
        assertEquals(12.0, new ManhattanDistance().d(x, y), 1E-9);
    }

    @Test
    public void testDistanceAllNaN() {
        System.out.println("distance all NaN");
        double[] x = {Double.NaN, Double.NaN};
        double[] y = {Double.NaN, Double.NaN};
        assertTrue(Double.isNaN(new ManhattanDistance().d(x, y)));
    }

    @Test
    public void testWeightedDistance() {
        System.out.println("weighted distance");
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {4.0, 5.0, 6.0};
        double[] weight = {1.0, 2.0, 3.0};
        ManhattanDistance dist = new ManhattanDistance(weight);
        // 1*3 + 2*3 + 3*3 = 3+6+9 = 18
        assertEquals(18.0, dist.d(x, y), 1E-9);
    }

    @Test
    public void testSparseDistance() {
        System.out.println("sparse distance");
        SparseArray s = new SparseArray();
        s.append(1, 1.0);
        s.append(2, 2.0);
        s.append(3, 3.0);

        SparseArray t = new SparseArray();
        t.append(1, 4.0);
        t.append(2, 3.0);
        t.append(3, 2.0);

        // |1-4| + |2-3| + |3-2| = 3+1+1 = 5
        assertEquals(5.0, new SparseManhattanDistance().d(s, t), 1E-9);
    }

    @Test
    public void testSparseDistanceWithMissingIndices() {
        System.out.println("sparse distance with missing indices");
        SparseArray s = new SparseArray();
        s.append(1, 3.0);

        SparseArray t = new SparseArray();
        t.append(2, 5.0);

        // index 1: |3-0|=3, index 2: |0-5|=5; total = 8
        assertEquals(8.0, new SparseManhattanDistance().d(s, t), 1E-9);
    }
}

