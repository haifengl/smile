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
public class MahalanobisDistanceTest {
    double[][] sigma =
    {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
    };

    public MahalanobisDistanceTest() {
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
     * Test of distance method, of class MahalanobisDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        double[] x = {1.2793, -0.1029, -1.5852};
        double[] y = {-0.2676, -0.1717, -1.8695};

        MahalanobisDistance instance = new MahalanobisDistance(sigma);
        assertEquals(2.703861, instance.d(x, y), 1E-6);
    }

}