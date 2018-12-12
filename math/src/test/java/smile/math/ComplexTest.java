/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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