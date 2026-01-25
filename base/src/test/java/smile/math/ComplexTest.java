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
package smile.math;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ComplexTest {
    Complex a = new Complex(5.0, 6.0);
    Complex b = new Complex(-3.0, 4.0);

    public ComplexTest() {
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
        System.out.println("Complex");
        System.out.println("a = " + a);
        System.out.println("b = " + b);
        assertEquals(5.0, a.re(), 1E-15);
        assertEquals(6.0, a.im(), 1E-15);
        assertEquals(7.810249675906654, a.abs(), 1E-15);
        assertEquals(Complex.of(2.0, 10.0), a.add(b));
        assertEquals(Complex.of(8.0, 2.0), a.sub(b));
        assertEquals(Complex.of(-39.0, 2.0), a.mul(b));
        assertEquals(Complex.of(0.36, -1.52), a.div(b));
        System.out.println("a / b = " + a.div(b));
        assertEquals(Complex.of(5.0, 6.0), a.div(b).mul(b));
        assertEquals(Complex.of(5.0, -6.0), a.conjugate());
        assertEquals(Complex.of(-6.685231390243073E-6, 1.00001031089812), a.tan());
    }

    /**
     * Test of Complex.Array.
     */
    @Test
    public void testArray() {
        System.out.println("Complex.Array");
        Complex.Array array = Complex.Array.of(a, b);
        System.out.println("a = " + a);
        System.out.println("b = " + b);
        assertEquals(a.re(), array.get(0).re(), 1E-15);
        assertEquals(a.im(), array.get(0).im(), 1E-15);
        assertEquals(b.re(), array.get(1).re(), 1E-15);
        assertEquals(b.im(), array.get(1).im(), 1E-15);

        Complex c = Complex.of(3.0);
        array.set(1, c);
        assertEquals(a.re(), array.get(0).re(), 1E-15);
        assertEquals(a.im(), array.get(0).im(), 1E-15);
        assertEquals(c.re(), array.get(1).re(), 1E-15);
        assertEquals(c.im(), array.get(1).im(), 1E-15);
    }
}