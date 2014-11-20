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
public class LeeDistanceTest {

    public LeeDistanceTest() {
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
     * Test of distance method, of class LeeDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        int[] x = {3, 3, 4, 0};
        int[] y = {2, 5, 4, 3};
        LeeDistance instance = new LeeDistance(6);
        assertEquals(6.0, instance.d(x, y), 1E-9);
    }
}