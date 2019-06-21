/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.math;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class ComplexTest {
    Complex a = new Complex(5.0, 6.0);
    Complex b = new Complex(-3.0, 4.0);

    public ComplexTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of all methods, of class Complex.
     */
    @Test
    public void testAll() {
        System.out.println("Complex");
        System.out.println("a = " + a);
        System.out.println("b = " + b);
        assertEquals(a.re, 5.0, 1E-15);
        assertEquals(a.im, 6.0, 1E-15);
        assertEquals(a.abs(), 7.810249675906654, 1E-15);
        assertTrue(a.add(b).equals(Complex.of(2.0, 10.0)));
        assertTrue(a.sub(b).equals(Complex.of(8.0, 2.0)));
        assertTrue(a.mul(b).equals(Complex.of(-39.0, 2.0)));
        assertTrue(a.div(b).equals(Complex.of(0.36, -1.52)));
        System.out.println("a / b = " + a.div(b));
        assertTrue(a.div(b).mul(b).equals(Complex.of(5.0, 6.0)));
        assertTrue(a.conjugate().equals(Complex.of(5.0, -6.0)));
        assertTrue(a.tan().equals(Complex.of(-6.685231390243073E-6, 1.00001031089812)));
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
        assertEquals(a.re, array.get(0).re, 1E-15);
        assertEquals(a.im, array.get(0).im, 1E-15);
        assertEquals(b.re, array.get(1).re, 1E-15);
        assertEquals(b.im, array.get(1).im, 1E-15);

        Complex c = Complex.of(3.0);
        array.set(1, c);
        assertEquals(a.re, array.get(0).re, 1E-15);
        assertEquals(a.im, array.get(0).im, 1E-15);
        assertEquals(c.re, array.get(1).re, 1E-15);
        assertEquals(c.im, array.get(1).im, 1E-15);
    }
}