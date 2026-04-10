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
     * Test of distance method for int, of class HammingDistance.
     */
    @Test
    public void testDistanceInt() {
        System.out.println("distance int");
        int x = 0x5D;
        int y = 0x49;
        assertEquals(2, HammingDistance.d(x, y));
    }

    /**
     * Test of distance for identical ints.
     */
    @Test
    public void testDistanceIntSame() {
        System.out.println("distance int same");
        assertEquals(0, HammingDistance.d(42, 42));
    }

    /**
     * Test of distance method for long, of class HammingDistance.
     */
    @Test
    public void testDistanceLong() {
        System.out.println("distance long");
        long x = 0xFFL;
        long y = 0x00L;
        assertEquals(8, HammingDistance.d(x, y));
        assertEquals(0, HammingDistance.d(x, x));
    }

    /**
     * Test of distance method for byte array, of class HammingDistance.
     */
    @Test
    public void testDistanceByteArray() {
        System.out.println("distance byte array");
        byte[] x = {1, 0, 1, 1, 1, 0, 1};
        byte[] y = {1, 0, 0, 1, 0, 0, 1};
        assertEquals(2, HammingDistance.d(x, y));
    }

    @Test
    public void testDistanceByteArraySame() {
        System.out.println("distance byte array same");
        byte[] x = {1, 0, 1};
        assertEquals(0, HammingDistance.d(x, x));
    }

    /**
     * Test of distance method for short array, of class HammingDistance.
     */
    @Test
    public void testDistanceShortArray() {
        System.out.println("distance short array");
        short[] x = {1, 2, 3, 4};
        short[] y = {1, 2, 0, 4};
        assertEquals(1, HammingDistance.d(x, y));
    }

    /**
     * Test of distance method for int array, of class HammingDistance.
     */
    @Test
    public void testDistanceIntArray() {
        System.out.println("distance int array");
        int[] x = {1, 2, 3, 4, 5};
        int[] y = {1, 0, 3, 0, 5};
        assertEquals(2.0, new HammingDistance().d(x, y), 1E-9);
    }

    /**
     * Test of distance method for BitSet, of class HammingDistance.
     */
    @Test
    public void testDistanceBitSet() {
        System.out.println("distance BitSet");

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

    @Test
    public void testDistanceLengthMismatch() {
        System.out.println("length mismatch");
        assertThrows(IllegalArgumentException.class,
                () -> HammingDistance.d(new byte[]{1, 2}, new byte[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class,
                () -> new HammingDistance().d(new int[]{1}, new int[]{1, 2}));
    }
}