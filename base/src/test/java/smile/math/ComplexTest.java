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

    @Test
    public void testNegate() {
        System.out.println("negate");
        assertEquals(Complex.of(-5.0, -6.0), a.negate());
        assertEquals(Complex.of(3.0, -4.0), b.negate());
    }

    @Test
    public void testLog() {
        System.out.println("log");
        // log(1) = 0
        Complex one = Complex.of(1.0);
        assertEquals(0.0, one.log().re(), 1E-15);
        assertEquals(0.0, one.log().im(), 1E-15);
        // log(e) = 1
        Complex e = Complex.of(Math.E);
        assertEquals(1.0, e.log().re(), 1E-14);
        assertEquals(0.0, e.log().im(), 1E-14);
        // log(-1) = i*pi
        Complex negOne = Complex.of(-1.0, 0.0);
        assertEquals(0.0, negOne.log().re(), 1E-15);
        assertEquals(Math.PI, negOne.log().im(), 1E-14);
    }

    @Test
    public void testSqrt() {
        System.out.println("sqrt");
        // sqrt(4) = 2
        Complex four = Complex.of(4.0);
        assertEquals(2.0, four.sqrt().re(), 1E-14);
        assertEquals(0.0, four.sqrt().im(), 1E-14);
        // sqrt(-1) = i
        Complex negOne = Complex.of(-1.0, 0.0);
        assertEquals(0.0, negOne.sqrt().re(), 1E-14);
        assertEquals(1.0, negOne.sqrt().im(), 1E-14);
        // sqrt(z)^2 = z
        Complex z = a.sqrt();
        Complex z2 = z.mul(z);
        assertEquals(a.re(), z2.re(), 1E-14);
        assertEquals(a.im(), z2.im(), 1E-14);
    }

    @Test
    public void testPow() {
        System.out.println("pow(double)");
        // (2+0i)^3 = 8
        Complex two = Complex.of(2.0);
        Complex result = two.pow(3.0);
        assertEquals(8.0, result.re(), 1E-12);
        assertEquals(0.0, result.im(), 1E-12);
        // (1+i)^2 = 2i
        Complex onePlusI = Complex.of(1.0, 1.0);
        Complex sq = onePlusI.pow(2.0);
        assertEquals(0.0, sq.re(), 1E-14);
        assertEquals(2.0, sq.im(), 1E-14);
    }

    @Test
    public void testPowComplex() {
        System.out.println("pow(Complex)");
        // i^i = e^(-pi/2)
        Complex i = Complex.of(0.0, 1.0);
        Complex result = i.pow(i);
        assertEquals(Math.exp(-Math.PI / 2), result.re(), 1E-14);
        assertEquals(0.0, result.im(), 1E-14);
    }

    @Test
    public void testScale() {
        System.out.println("scale");
        assertEquals(Complex.of(10.0, 12.0), a.scale(2.0));
        assertEquals(Complex.of(2.5, 3.0), a.scale(0.5));
    }

    @Test
    public void testReciprocal() {
        System.out.println("reciprocal");
        // 1/(a) * a = 1
        Complex prod = a.reciprocal().mul(a);
        assertEquals(1.0, prod.re(), 1E-14);
        assertEquals(0.0, prod.im(), 1E-14);
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

    @Test
    public void testArrayIterator() {
        System.out.println("Complex.Array iterator");
        Complex.Array array = Complex.Array.of(a, b);
        var it = array.iterator();
        assertTrue(it.hasNext());
        Complex first = it.next();
        assertEquals(a.re(), first.re(), 1E-15);
        assertEquals(a.im(), first.im(), 1E-15);
        assertTrue(it.hasNext());
        Complex second = it.next();
        assertEquals(b.re(), second.re(), 1E-15);
        assertEquals(b.im(), second.im(), 1E-15);
        assertFalse(it.hasNext());
    }

    @Test
    public void testArrayApply() {
        System.out.println("Complex.Array apply/update");
        Complex.Array array = new Complex.Array(2);
        array.update(0, a);
        array.update(1, 7.0);  // real only set
        assertEquals(a.re(), array.apply(0).re(), 1E-15);
        assertEquals(a.im(), array.apply(0).im(), 1E-15);
        assertEquals(7.0, array.apply(1).re(), 1E-15);
        assertEquals(0.0, array.apply(1).im(), 1E-15);  // im not set
    }
}
