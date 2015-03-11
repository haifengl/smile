/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.interpolation;

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
public class LinearInterpolationTest {

    public LinearInterpolationTest() {
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