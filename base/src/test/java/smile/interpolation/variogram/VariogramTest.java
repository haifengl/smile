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
package smile.interpolation.variogram;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all {@link Variogram} implementations.
 *
 * @author Haifeng Li
 */
public class VariogramTest {

    // -----------------------------------------------------------------------
    // PowerVariogram
    // -----------------------------------------------------------------------

    @Test
    public void testPowerVariogramAtZero() {
        System.out.println("PowerVariogram.f(0) = nugget");
        double[][] x = {{0, 0}, {1, 0}, {0, 1}};
        double[] y = {0.0, 1.0, 1.0};
        PowerVariogram v = new PowerVariogram(x, y);
        assertEquals(0.0, v.f(0.0), 1E-10);
    }

    @Test
    public void testPowerVariogramMonotone() {
        System.out.println("PowerVariogram.monotone increasing");
        double[][] x = {{0, 0}, {1, 0}, {2, 0}, {3, 0}};
        double[] y = {0.0, 1.0, 2.0, 3.0};
        PowerVariogram v = new PowerVariogram(x, y);
        double prev = v.f(0.0);
        for (double r = 0.5; r <= 5.0; r += 0.5) {
            double curr = v.f(r);
            assertTrue(curr >= prev, "Should be monotone increasing at r=" + r);
            prev = curr;
        }
    }

    @Test
    public void testPowerVariogramNugget() {
        System.out.println("PowerVariogram.nugget effect");
        double[][] x = {{0, 0}, {1, 0}, {0, 1}};
        double[] y = {0.0, 1.0, 1.0};
        double nugget = 0.5;
        PowerVariogram v = new PowerVariogram(x, y, 1.5, nugget);
        assertEquals(nugget, v.f(0.0), 1E-10);
        assertTrue(v.f(1.0) > nugget);
    }

    @Test
    public void testPowerVariogramInvalidBeta() {
        System.out.println("PowerVariogram.invalid beta");
        double[][] x = {{0, 0}, {1, 0}};
        double[] y = {0.0, 1.0};
        assertThrows(IllegalArgumentException.class,
                () -> new PowerVariogram(x, y, 0.5));   // beta < 1
        assertThrows(IllegalArgumentException.class,
                () -> new PowerVariogram(x, y, 2.0));   // beta >= 2
    }

    @Test
    public void testPowerVariogramToString() {
        double[][] x = {{0, 0}, {1, 0}};
        double[] y = {0.0, 1.0};
        PowerVariogram v = new PowerVariogram(x, y);
        assertNotNull(v.toString());
        assertTrue(v.toString().contains("Variogram"));
    }

    // -----------------------------------------------------------------------
    // GaussianVariogram
    // -----------------------------------------------------------------------

    @Test
    public void testGaussianVariogramAtZero() {
        System.out.println("GaussianVariogram.f(0) = nugget (c)");
        GaussianVariogram v = new GaussianVariogram(1.0, 1.0, 0.1);
        assertEquals(0.1, v.f(0.0), 1E-10);
    }

    @Test
    public void testGaussianVariogramSill() {
        System.out.println("GaussianVariogram.approaches sill b+c as r→∞");
        GaussianVariogram v = new GaussianVariogram(1.0, 2.0, 0.0);
        // f(r) = b * (1 - exp(-3r^2/a^2)) → b as r → ∞
        assertEquals(2.0, v.f(1000.0), 1E-6);
    }

    @Test
    public void testGaussianVariogramMonotone() {
        System.out.println("GaussianVariogram.monotone increasing");
        GaussianVariogram v = new GaussianVariogram(2.0, 5.0);
        double prev = v.f(0.0);
        for (double r = 0.5; r <= 10.0; r += 0.5) {
            double curr = v.f(r);
            assertTrue(curr >= prev, "Should be monotone increasing at r=" + r);
            prev = curr;
        }
    }

    @Test
    public void testGaussianVariogramInvalidParams() {
        System.out.println("GaussianVariogram.invalid parameters");
        assertThrows(IllegalArgumentException.class, () -> new GaussianVariogram(0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new GaussianVariogram(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new GaussianVariogram(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GaussianVariogram(1.0, 1.0, -0.1));
    }

    @Test
    public void testGaussianVariogramToString() {
        GaussianVariogram v = new GaussianVariogram(1.0, 2.0);
        assertNotNull(v.toString());
        assertTrue(v.toString().contains("Variogram"));
    }

    // -----------------------------------------------------------------------
    // ExponentialVariogram
    // -----------------------------------------------------------------------

    @Test
    public void testExponentialVariogramAtZero() {
        System.out.println("ExponentialVariogram.f(0) = nugget");
        ExponentialVariogram v = new ExponentialVariogram(1.0, 1.0, 0.2);
        assertEquals(0.2, v.f(0.0), 1E-10);
    }

    @Test
    public void testExponentialVariogramSill() {
        System.out.println("ExponentialVariogram.approaches sill as r→∞");
        ExponentialVariogram v = new ExponentialVariogram(1.0, 3.0, 0.0);
        assertEquals(3.0, v.f(1000.0), 1E-3);
    }

    @Test
    public void testExponentialVariogramMonotone() {
        System.out.println("ExponentialVariogram.monotone increasing");
        ExponentialVariogram v = new ExponentialVariogram(2.0, 5.0);
        double prev = v.f(0.0);
        for (double r = 0.5; r <= 10.0; r += 0.5) {
            double curr = v.f(r);
            assertTrue(curr >= prev, "Should be monotone increasing at r=" + r);
            prev = curr;
        }
    }

    @Test
    public void testExponentialVariogramInvalidParams() {
        System.out.println("ExponentialVariogram.invalid parameters");
        assertThrows(IllegalArgumentException.class, () -> new ExponentialVariogram(0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialVariogram(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialVariogram(1.0, 1.0, -0.5));
    }

    @Test
    public void testExponentialVariogramToString() {
        ExponentialVariogram v = new ExponentialVariogram(1.0, 2.0);
        assertNotNull(v.toString());
        assertTrue(v.toString().contains("Variogram"));
    }

    // -----------------------------------------------------------------------
    // SphericalVariogram
    // -----------------------------------------------------------------------

    @Test
    public void testSphericalVariogramAtZero() {
        System.out.println("SphericalVariogram.f(0) = nugget");
        SphericalVariogram v = new SphericalVariogram(2.0, 1.0, 0.3);
        assertEquals(0.3, v.f(0.0), 1E-10);
    }

    @Test
    public void testSphericalVariogramAtRange() {
        System.out.println("SphericalVariogram.f(a) = b + c (sill)");
        double a = 3.0, b = 2.0, c = 0.5;
        SphericalVariogram v = new SphericalVariogram(a, b, c);
        // At r = a: f(a) = c + b*(1.5*1 - 0.5*1^3) = c + b
        assertEquals(c + b, v.f(a), 1E-10);
    }

    @Test
    public void testSphericalVariogramBeyondRange() {
        System.out.println("SphericalVariogram.flat beyond range");
        SphericalVariogram v = new SphericalVariogram(1.0, 2.0);
        double atRange = v.f(1.0);
        assertEquals(atRange, v.f(2.0), 1E-10);
        assertEquals(atRange, v.f(100.0), 1E-10);
    }

    @Test
    public void testSphericalVariogramMonotone() {
        System.out.println("SphericalVariogram.monotone up to range");
        SphericalVariogram v = new SphericalVariogram(5.0, 3.0);
        double prev = v.f(0.0);
        for (double r = 0.5; r <= 5.0; r += 0.5) {
            double curr = v.f(r);
            assertTrue(curr >= prev - 1E-10, "Should be monotone at r=" + r);
            prev = curr;
        }
    }

    @Test
    public void testSphericalVariogramInvalidParams() {
        System.out.println("SphericalVariogram.invalid parameters");
        assertThrows(IllegalArgumentException.class, () -> new SphericalVariogram(0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new SphericalVariogram(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new SphericalVariogram(1.0, 1.0, -0.1));
    }

    @Test
    public void testSphericalVariogramToString() {
        SphericalVariogram v = new SphericalVariogram(1.0, 2.0);
        assertNotNull(v.toString());
        assertTrue(v.toString().contains("Variogram"));
    }
}

