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
public class LaplaceInterpolationTest {

    public LaplaceInterpolationTest() {
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
     * Test of interpolate method, of class LaplaceInterpolation.
     */
    @Test
    public void testInterpolate() {
        System.out.println("interpolate");
        double[][] matrix = {{0, Double.NaN}, {1, 2}};
        double error = LaplaceInterpolation.interpolate(matrix);
        assertEquals(0, matrix[0][0], 1E-7);
        assertEquals(1, matrix[1][0], 1E-7);
        assertEquals(2, matrix[1][1], 1E-7);
        assertEquals(1, matrix[0][1], 1E-7);
        assertTrue(error < 1E-6);
    }
}