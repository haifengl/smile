/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

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
     * Test of toString method, of class Complex.
     */
    @Test
    public void testAll() {
        System.out.println("Complex");
        System.out.println("a = " + a);
        System.out.println("b = " + b);
        assertEquals(a.re(), 5.0, 1E-15);
        assertEquals(a.im(), 6.0, 1E-15);
        assertEquals(a.abs(), 7.810249675906654, 1E-15);
        assertTrue(a.plus(b).equals(new Complex(2.0, 10.0)));
        assertTrue(a.minus(b).equals(new Complex(8.0, 2.0)));
        assertTrue(a.times(b).equals(new Complex(-39.0, 2.0)));
        assertTrue(a.div(b).equals(new Complex(0.36, -1.52)));
        System.out.println("a / b = " + a.div(b));
        assertTrue(a.div(b).times(b).equals(new Complex(5.0, 6.0)));
        assertTrue(a.conjugate().equals(new Complex(5.0, -6.0)));
        assertTrue(a.tan().equals(new Complex(-6.685231390243073E-6, 1.00001031089812)));
    }
}