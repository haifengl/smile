/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.projection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.Math;

/**
 *
 * @author Haifeng Li
 */
public class RandomProjectionTest {

    public RandomProjectionTest() {
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
     * Test of getProjection method, of class RandomProjection.
     */
    @Test
    public void testRandomProjection() {
        System.out.println("getProjection");
        RandomProjection instance = new RandomProjection(128, 40);

        double[][] p = instance.getProjection();
        double[][] t = Math.aatmm(p);

        for (int i = 0; i < t.length; i++) {
            for (int j = 0; j < t[i].length; j++) {
                System.out.format("% .4f ", t[i][j]);
            }
            System.out.println();
        }

        assertTrue(Math.equals(Math.eye(40), t, 1E-10));
    }
}