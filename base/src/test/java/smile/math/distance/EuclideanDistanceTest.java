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
}