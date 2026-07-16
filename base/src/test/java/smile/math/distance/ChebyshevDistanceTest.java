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
public class ChebyshevDistanceTest {

    public ChebyshevDistanceTest() {
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
     * Test of distance method, of class ChebyshevDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        double[] x = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] y = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        assertEquals(2.00286, new ChebyshevDistance().d(x, y), 1E-5);
    }

    @Test
    public void testDistanceInt() {
        System.out.println("distance int");
        int[] x = {1, 2, 3, 4};
        int[] y = {4, 3, 2, 1};
        assertEquals(3.0, ChebyshevDistance.d(x, y), 1E-9);
    }

    @Test
    public void testDistanceSameVector() {
        System.out.println("distance same vector");
        double[] x = {1.0, 2.0, 3.0};
        assertEquals(0.0, new ChebyshevDistance().d(x, x), 1E-9);
    }

    @Test
    public void testDistanceWithNaN() {
        System.out.println("distance with NaN");
        double[] x = {Double.NaN, 2.0, 5.0};
        double[] y = {1.0, Double.NaN, 1.0};
        // Only non-NaN pair (index 2): |5-1| = 4
        assertEquals(4.0, new ChebyshevDistance().d(x, y), 1E-9);
    }

    @Test
    public void testSparseChebyshevDistance() {
        System.out.println("sparse Chebyshev distance");
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

        // max of |1-4|, |2-3|, |3-2|, |4-1| = 3
        assertEquals(3.0, new SparseChebyshevDistance().d(s, t), 1E-9);
    }

    @Test
    public void testSparseChebyshevDistanceWithMissingIndices() {
        System.out.println("sparse Chebyshev distance with missing indices");
        SparseArray s = new SparseArray();
        s.append(2, 2.0);
        s.append(4, 4.0);

        SparseArray t = new SparseArray();
        t.append(1, 5.0);
        t.append(3, 1.0);

        // index 1: absent in s -> |0-5|=5, index 2: absent in t -> |2-0|=2
        // index 3: absent in s -> |0-1|=1, index 4: absent in t -> |4-0|=4
        // max = 5
        assertEquals(5.0, new SparseChebyshevDistance().d(s, t), 1E-9);
    }

    @Test
    public void testSparseChebyshevDistanceSameVector() {
        System.out.println("sparse Chebyshev distance same vector");
        SparseArray s = new SparseArray();
        s.append(1, 3.0);
        s.append(2, -5.0);

        assertEquals(0.0, new SparseChebyshevDistance().d(s, s), 1E-9);
    }
}

