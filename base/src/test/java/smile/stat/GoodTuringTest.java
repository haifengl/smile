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

package smile.stat;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GoodTuringTest {

    public GoodTuringTest() {
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

    @Test
    public void test() {
        System.out.println("GoodTuring");
        int[] r = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12};
        int[] Nr = {120, 40, 24, 13, 15, 5, 11, 2, 2, 1, 3};
        double p0 = 0.2047782;
        double[] p = {
                0.0009267, 0.0024393, 0.0040945, 0.0058063, 0.0075464,
                0.0093026, 0.0110689, 0.0128418, 0.0146194, 0.0164005, 0.0199696};

        GoodTuring result = GoodTuring.of(r, Nr);
        assertEquals(p0, result.p0, 1E-7);
        for (int i = 0; i < r.length; i++) {
            assertEquals(p[i], result.p[i], 1E-7);
        }
    }
}
