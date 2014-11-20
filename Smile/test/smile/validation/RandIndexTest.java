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
public class RandIndexTest {

    public RandIndexTest() {
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
     * Test of measure method, of class RandIndex.
     */
    @Test
    public void testMeasure() {
        System.out.println("measure");
        int[] clusters = {2, 3, 3, 1, 1, 3, 3, 1, 3, 1, 1, 3, 3, 3, 3, 3, 2, 3, 3, 1, 1, 1, 1, 1, 1, 4, 1, 3, 3, 3, 3, 3, 1, 4, 4, 4, 3, 1, 1, 3, 1, 4, 3, 3, 3, 3, 1, 1, 3, 1, 1, 3, 3, 3, 3, 4, 3, 1, 3, 1, 3, 1, 1, 1, 1, 1, 3, 3, 2, 3, 3, 1, 1, 3, 3, 3, 3, 3, 3, 1, 1, 3, 2, 3, 2, 2, 4, 1, 3, 1, 3, 1, 1, 3, 4, 4, 4, 1, 2, 3, 1, 1, 3, 1, 1, 1, 4, 3, 3, 2, 3, 3, 1, 3, 3, 1, 1, 1, 3, 4, 4, 2, 3, 3, 3, 3, 1, 1, 1, 3, 3, 3, 2, 3, 3, 3, 2, 3, 3, 1, 3, 1, 3, 3, 1, 1, 3, 3, 3, 1, 1, 1, 1, 3, 3, 4, 3, 2, 3, 1, 1, 3, 1, 2, 3, 1, 1, 3, 3, 1, 1, 1, 1, 1, 3, 1, 3, 1, 3, 1, 3, 1, 1, 3, 1, 1, 1, 3, 2, 1, 2, 1, 1, 1, 1, 1, 3, 1, 1, 3, 3, 1, 3, 3, 3};
        int[] alt      = {3, 2, 2, 0, 0, 2, 2, 0, 2, 0, 0, 2, 2, 2, 2, 2, 3, 2, 2, 0, 0, 0, 0, 0, 0, 3, 0, 2, 2, 2, 2, 2, 0, 3, 3, 3, 2, 0, 0, 2, 0, 3, 2, 2, 2, 2, 0, 0, 2, 0, 0, 2, 2, 2, 2, 3, 2, 0, 2, 0, 2, 0, 0, 0, 0, 0, 2, 2, 3, 2, 2, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 2, 3, 2, 0, 3, 3, 0, 2, 0, 2, 0, 0, 2, 3, 3, 3, 0, 3, 2, 0, 0, 2, 0, 0, 0, 3, 2, 2, 3, 2, 2, 0, 2, 2, 0, 0, 0, 2, 3, 3, 3, 2, 2, 2, 2, 0, 0, 0, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 0, 2, 0, 2, 2, 0, 0, 2, 1, 2, 0, 0, 0, 0, 2, 2, 3, 2, 1, 2, 0, 0, 2, 0, 3, 2, 0, 0, 2, 2, 0, 0, 0, 0, 0, 2, 0, 2, 0, 2, 0, 0, 0, 0, 2, 0, 0, 0, 2, 3, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 2, 2, 0, 2, 2, 2};
        RandIndex instance = new RandIndex();
        double expResult = 0.9651;
        double result = instance.measure(clusters, alt);
        assertEquals(expResult, result, 1E-4);
    }

}