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
public class CubicSplineInterpolation2DTest {

    public CubicSplineInterpolation2DTest() {
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
     * Test of interpolate method, of class CubicSplineInterpolation2D.
     */
    @Test
    public void testInterpolate() {
        System.out.println("interpolate");
        double[] x1 = {1950, 1960, 1970, 1980, 1990};
        double[] x2 = {10, 20, 30};
        double[][] y = {
            {150.697, 199.592, 187.625},
            {179.323, 195.072, 250.287},
            {203.212, 179.092, 322.767},
            {226.505, 153.706, 426.730},
            {249.633, 120.281, 598.243}
        };

        CubicSplineInterpolation2D instance = new CubicSplineInterpolation2D(x1, x2, y);
        assertEquals(167.9922755, instance.interpolate(1975, 15), 1E-7);
        assertEquals(167.5167746, instance.interpolate(1975, 20), 1E-7);
        assertEquals(244.3006193, instance.interpolate(1975, 25), 1E-7);
    }
}