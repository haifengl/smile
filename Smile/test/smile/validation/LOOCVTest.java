/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.validation;

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
public class LOOCVTest {

    public LOOCVTest() {
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
     * Test if the train and test dataset are complete, of class LeaveOneOut.
     */
    @Test
    public void testComplete() {
        System.out.println("Complete");
        int n = 57;
        LOOCV instance = new LOOCV(n);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                hit[j] = false;
            }

            int[] train = instance.train[i];
            for (int j = 0; j < train.length; j++) {
                assertFalse(hit[train[j]]);
                hit[train[j]] = true;
            }

            int test = instance.test[i];
            assertFalse(hit[test]);
            hit[test] = true;

            for (int j = 0; j < n; j++) {
                assertTrue(hit[j]);
            }
        }
    }

    /**
     * Test if different dataset are different, of class LeaveOneOut.
     */
    @Test
    public void testOrthogonal() {
        System.out.println("Orthogonal");
        int n = 57;
        LOOCV instance = new LOOCV(n);
        boolean[] hit = new boolean[n];
        for (int i = 0; i < n; i++) {
            int test = instance.test[i];
            assertFalse(hit[test]);
            hit[test] = true;
        }

        for (int j = 0; j < n; j++) {
            assertTrue(hit[j]);
        }
    }

}