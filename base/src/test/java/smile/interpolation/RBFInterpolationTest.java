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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.interpolation;

import smile.math.rbf.GaussianRadialBasis;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RBFInterpolationTest {

    public RBFInterpolationTest() {
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
        RBFInterpolation instance = new RBFInterpolation(x, y, new GaussianRadialBasis());
        double[] x1 = {0.5, 0.5};
        assertEquals(0, instance.interpolate(x[0]), 1E-7);
        assertEquals(1, instance.interpolate(x[1]), 1E-7);
        assertEquals(0.569349, instance.interpolate(x1), 1E-6);
    }

    @Test
    public void testInterpolate2D() {
        System.out.println("interpolate 2d");
        double[] x1 = {0, 1};
        double[] x2 = {0, 1};
        double[] y = {0, 1};
        RBFInterpolation2D instance = new RBFInterpolation2D(x1, x2, y, new GaussianRadialBasis());
        assertEquals(0, instance.interpolate(0, 0), 1E-7);
        assertEquals(1, instance.interpolate(1, 1), 1E-7);
        assertEquals(0.569349, instance.interpolate(0.5, 0.5), 1E-6);
    }
}