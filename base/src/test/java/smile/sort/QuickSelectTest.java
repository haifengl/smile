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

package smile.sort;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class QuickSelectTest {

    public QuickSelectTest() {
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
     * Test of median method, of class QuickSelect.
     */
    @Test
    public void testMedianInt() {
        System.out.println("median int");
        int[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1));
        int[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2));
        int[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3));
        int[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4));
        int[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5));
        int[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6));
    }

    /**
     * Test of median method, of class QuickSelect.
     */
    @Test
    public void testMedianFloat() {
        System.out.println("median float");
        float[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1), 1E-10);
        float[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2), 1E-10);
        float[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3), 1E-10);
        float[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4), 1E-10);
        float[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5), 1E-10);
        float[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6), 1E-10);
    }

    /**
     * Test of median method, of class QuickSelect.
     */
    @Test
    public void testMedianDouble() {
        System.out.println("median double");
        double[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1), 1E-10);
        double[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2), 1E-10);
        double[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3), 1E-10);
        double[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4), 1E-10);
        double[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5), 1E-10);
        double[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6), 1E-10);
    }

    /**
     * Test of median method, of class QuickSelect.
     */
    @Test
    public void testMedianObject() {
        System.out.println("median object");
        Integer[] data1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data1), 1E-10);
        Integer[] data2 = {5, 2, 3, 4, 1, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data2), 1E-10);
        Integer[] data3 = {1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data3), 1E-10);
        Integer[] data4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data4), 1E-10);
        Integer[] data5 = {5, 1, 2, 3, 4, 0, 6, 7, 8, 9};
        assertEquals(5, QuickSelect.median(data5), 1E-10);
        Integer[] data6 = {0, 1, 2, 3, 4, 9, 6, 7, 8, 5};
        assertEquals(5, QuickSelect.median(data6), 1E-10);
    }
}
