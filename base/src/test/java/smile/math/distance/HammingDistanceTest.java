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

package smile.math.distance;

import java.util.BitSet;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class HammingDistanceTest {

    public HammingDistanceTest() {
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
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        int x = 0x5D;
        int y = 0x49;
        assertEquals(2, HammingDistance.d(x, y));
    }

    /**
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistanceArray() {
        System.out.println("distance");
        byte[] x = {1, 0, 1, 1, 1, 0, 1};
        byte[] y = {1, 0, 0, 1, 0, 0, 1};
        assertEquals(2, HammingDistance.d(x, y));
    }

    /**
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistanceBitSet() {
        System.out.println("distance");

        BitSet x = new BitSet();
        x.set(1);
        x.set(3);
        x.set(4);
        x.set(5);
        x.set(7);

        BitSet y = new BitSet();
        y.set(1);
        y.set(4);
        y.set(7);

        HammingDistance hamming = new HammingDistance();
        assertEquals(2, hamming.d(x, y), 1E-9);
    }
}