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
public class MinkowskiDistanceTest {

    public MinkowskiDistanceTest() {
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
     * Test of distance method, of class MinkowskiDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        double[] x = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] y = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        MinkowskiDistance m3 = new MinkowskiDistance(3);
        MinkowskiDistance m4 = new MinkowskiDistance(4);
        assertEquals(2.124599, m3.d(x, y), 1E-6);
        assertEquals(2.044696, m4.d(x, y), 1E-6);
    }

    @Test
    public void testDistanceIntArray() {
        System.out.println("distance int array");
        int[] x = {1, 2, 3, 4};
        int[] y = {4, 3, 2, 1};
        MinkowskiDistance m3 = new MinkowskiDistance(3);
        // sum of |di|^3 = 3^3 + 1^3 + 1^3 + 3^3 = 27+1+1+27 = 56; cbrt(56) = 3.8259...
        assertEquals(Math.pow(56.0, 1.0/3), m3.d(x, y), 1E-6);
    }

    @Test
    public void testDistanceSameVector() {
        System.out.println("distance same vector");
        double[] x = {1.0, 2.0, 3.0};
        assertEquals(0.0, new MinkowskiDistance(3).d(x, x), 1E-9);
    }

    @Test
    public void testDistanceWithNaN() {
        System.out.println("distance with NaN");
        // only index 2: |5-1| = 4; n=3, m=1; dist = pow(3 * 4^3 / 1, 1/3) = pow(192, 1/3)
        double[] x = {Double.NaN, 2.0, 5.0};
        double[] y = {1.0, Double.NaN, 1.0};
        assertEquals(Math.pow(3.0 * 64.0, 1.0/3), new MinkowskiDistance(3).d(x, y), 1E-9);
    }

    @Test
    public void testDistanceAllNaN() {
        System.out.println("distance all NaN");
        double[] x = {Double.NaN, Double.NaN};
        double[] y = {Double.NaN, Double.NaN};
        assertTrue(Double.isNaN(new MinkowskiDistance(3).d(x, y)));
    }

    @Test
    public void testWeightedDistance() {
        System.out.println("weighted distance");
        double[] x = {1.0, 0.0};
        double[] y = {0.0, 0.0};
        double[] weight = {8.0, 1.0};
        // pow(8 * 1^3, 1/3) = pow(8, 1/3) = 2
        assertEquals(2.0, new MinkowskiDistance(3, weight).d(x, y), 1E-9);
    }

    @Test
    public void testSparseDistance() {
        System.out.println("sparse distance");
        SparseArray s = new SparseArray();
        s.append(1, 1.0);
        s.append(2, 2.0);

        SparseArray t = new SparseArray();
        t.append(1, 4.0);
        t.append(2, 5.0);

        // p=3: pow(3^3 + 3^3, 1/3) = pow(54, 1/3)
        assertEquals(Math.pow(54.0, 1.0/3), new SparseMinkowskiDistance(3).d(s, t), 1E-9);
    }

    @Test
    public void testSparseDistanceNegativeValues() {
        System.out.println("sparse distance negative values");
        SparseArray s = new SparseArray();
        s.append(1, -3.0);
        s.append(2, 4.0);

        SparseArray t = new SparseArray();
        t.append(1, 1.0);
        t.append(2, 4.0);

        // index 1: |-3 - 1| = 4; index 2: |4-4|=0 (same, not counted since diff=0)
        // but SparseArray stores all non-zero values:
        // p=3: pow(|(-3)-1|^3 + |4-4|^3, 1/3) = pow(64, 1/3) = 4
        assertEquals(4.0, new SparseMinkowskiDistance(3).d(s, t), 1E-9);
    }

    @Test
    public void testInvalidOrder() {
        System.out.println("invalid order");
        assertThrows(IllegalArgumentException.class, () -> new MinkowskiDistance(0));
        assertThrows(IllegalArgumentException.class, () -> new MinkowskiDistance(-1));
    }
}

