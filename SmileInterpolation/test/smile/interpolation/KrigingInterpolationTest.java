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
public class KrigingInterpolationTest {

    public KrigingInterpolationTest() {
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
     * Test of interpolate method, of class KrigingInterpolation.
     */
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

}