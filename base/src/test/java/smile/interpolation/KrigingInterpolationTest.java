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

package smile.interpolation;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class KrigingInterpolationTest {

    public KrigingInterpolationTest() {
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
    public void testInterpolate() {
        System.out.println("interpolate");
        double[][] x = {{0, 0}, {1, 1}};
        double[] y = {0, 1};
        KrigingInterpolation instance = new KrigingInterpolation(x, y);
        double[] x1 = {0.5, 0.5};
        assertEquals(0, instance.interpolate(x[0]), 1E-7);
        assertEquals(1, instance.interpolate(x[1]), 1E-7);
        assertEquals(0.5, instance.interpolate(x1), 1E-7);
    }

    @Test
    public void testInterpolate2D() {
        System.out.println("interpolate 2d");
        double[] x1 = {0, 1};
        double[] x2 = {0, 1};
        double[] y = {0, 1};
        KrigingInterpolation2D instance = new KrigingInterpolation2D(x1, x2, y);
        assertEquals(0, instance.interpolate(0, 0), 1E-7);
        assertEquals(1, instance.interpolate(1, 1), 1E-7);
        assertEquals(0.5, instance.interpolate(0.5, 0.5), 1E-7);
    }
}