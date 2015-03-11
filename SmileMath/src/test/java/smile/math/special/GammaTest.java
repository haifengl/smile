/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.special;

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
public class GammaTest {

    public GammaTest() {
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
     * Test of gamma method, of class Gamma.
     */
    @Test
    public void testGamma() {
        System.out.println("gamma");
        assertTrue(Double.isInfinite(Gamma.gamma(0)));

        assertEquals(1.0, Gamma.gamma(1), 1E-7);
        assertEquals(1.0, Gamma.gamma(2), 1E-7);
        assertEquals(2.0, Gamma.gamma(3), 1E-7);
        assertEquals(6.0, Gamma.gamma(4), 1E-7);

        assertEquals(0.886227, Gamma.gamma(1.5), 1E-6);
        assertEquals(1.329340, Gamma.gamma(2.5), 1E-6);
        assertEquals(3.323351, Gamma.gamma(3.5), 1E-6);
        assertEquals(11.63173, Gamma.gamma(4.5), 1E-5);
    }

    /**
     * Test of logGamma method, of class Gamma.
     */
    @Test
    public void testLogGamma() {
        System.out.println("logGamma");
        assertTrue(Double.isInfinite(Gamma.logGamma(0)));

        assertEquals(0.0, Gamma.logGamma(1), 1E-7);
        assertEquals(0, Gamma.logGamma(2), 1E-7);
        assertEquals(Math.log(2.0), Gamma.logGamma(3), 1E-7);
        assertEquals(Math.log(6.0), Gamma.logGamma(4), 1E-7);

        assertEquals(-0.1207822, Gamma.logGamma(1.5), 1E-7);
        assertEquals(0.2846829, Gamma.logGamma(2.5), 1E-7);
        assertEquals(1.200974, Gamma.logGamma(3.5), 1E-6);
        assertEquals(2.453737, Gamma.logGamma(4.5), 1E-6);
    }

    /**
     * Test of incompleteGamma method, of class Gamma.
     */
    @Test
    public void testIncompleteGamma() {
        System.out.println("incompleteGamma");
        assertEquals(0.7807, Gamma.regularizedIncompleteGamma(2.1, 3), 1E-4);
        assertEquals(0.3504, Gamma.regularizedIncompleteGamma(3, 2.1), 1E-4);
    }

    /**
     * Test of upperIncompleteGamma method, of class Gamma.
     */
    @Test
    public void testUpperIncompleteGamma() {
        System.out.println("incompleteGamma");
        assertEquals(0.2193, Gamma.regularizedUpperIncompleteGamma(2.1, 3), 1E-4);
        assertEquals(0.6496, Gamma.regularizedUpperIncompleteGamma(3, 2.1), 1E-4);
    }
}