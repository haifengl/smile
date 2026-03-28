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
package smile.interpolation;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class LinearInterpolationTest {

    public LinearInterpolationTest() {
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
     * Test of interpolate method, of class LinearInterpolation.
     */
    @Test
    public void testInterpolate() {
        System.out.println("interpolate");
        double[] x = {0, 1, 2, 3, 4, 5, 6};
        double[] y = {0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794};
        LinearInterpolation instance = new LinearInterpolation(x, y);
        assertEquals(0.5252, instance.interpolate(2.5), 1E-4);
    }
}