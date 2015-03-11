/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.distance;

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
public class ManhattanDistanceTest {

    public ManhattanDistanceTest() {
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
     * Test of distance method, of class ManhattanDistance.
     */
    @Test
    public void testDistanceInt() {
        System.out.println("distance");
        int[] x = {1, 2, 3, 4};
        int[] y = {4, 3, 2, 1};
        assertEquals(8, new ManhattanDistance().d(x, y), 1E-6);
    }

    /**
     * Test of distance method, of class ManhattanDistance.
     */
    @Test
    public void testDistanceDouble() {
        System.out.println("distance");
        double[] x = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] y = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        assertEquals(4.485227, new ManhattanDistance().d(x, y), 1E-6);
    }
}