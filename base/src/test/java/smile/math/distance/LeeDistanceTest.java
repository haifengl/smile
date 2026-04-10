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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class LeeDistanceTest {

    public LeeDistanceTest() {
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
     * Test of distance method, of class LeeDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        int[] x = {3, 3, 4, 0};
        int[] y = {2, 5, 4, 3};
        LeeDistance instance = new LeeDistance(6);
        assertEquals(6.0, instance.d(x, y), 1E-9);
    }

    @Test
    public void testSameVector() {
        System.out.println("same vector");
        int[] x = {1, 2, 3};
        LeeDistance instance = new LeeDistance(10);
        assertEquals(0.0, instance.d(x, x), 1E-9);
    }

    @Test
    public void testSymmetry() {
        System.out.println("symmetry");
        int[] x = {0, 1, 5};
        int[] y = {3, 4, 2};
        LeeDistance instance = new LeeDistance(6);
        assertEquals(instance.d(x, y), instance.d(y, x), 1E-9);
    }

    @Test
    public void testWrapAround() {
        System.out.println("wrap around");
        // q=6: min(|0-5|, 6-|0-5|) = min(5, 1) = 1
        int[] x = {0};
        int[] y = {5};
        LeeDistance instance = new LeeDistance(6);
        assertEquals(1.0, instance.d(x, y), 1E-9);
    }

    @Test
    public void testQ2EqualsHamming() {
        System.out.println("q=2 equals Hamming for binary");
        // For q=2, Lee distance == Hamming distance on binary alphabet
        int[] x = {0, 1, 0, 1};
        int[] y = {1, 1, 0, 0};
        LeeDistance lee = new LeeDistance(2);
        assertEquals(2.0, lee.d(x, y), 1E-9);
    }

    @Test
    public void testLengthMismatch() {
        System.out.println("length mismatch");
        LeeDistance instance = new LeeDistance(6);
        assertThrows(IllegalArgumentException.class,
                () -> instance.d(new int[]{1, 2}, new int[]{1}));
    }

    @Test
    public void testInvalidQ() {
        System.out.println("invalid q");
        assertThrows(IllegalArgumentException.class, () -> new LeeDistance(1));
        assertThrows(IllegalArgumentException.class, () -> new LeeDistance(0));
    }
}