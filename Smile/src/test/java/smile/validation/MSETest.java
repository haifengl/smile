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
public class MSETest {

    public MSETest() {
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
     * Test of measure method, of class MSE.
     */
    @Test
    public void testMeasure() {
        System.out.println("measure");
        double[] truth = {
            83.0,  88.5,  88.2,  89.5,  96.2,  98.1,  99.0, 100.0, 101.2,
            104.6, 108.4, 110.8, 112.6, 114.2, 115.7, 116.9
        };
        
        double[] prediction = {
            83.60082, 86.94973, 88.09677, 90.73065, 96.53551, 97.83067,
            98.12232, 99.87776, 103.20861, 105.08598, 107.33369, 109.57251,
            112.98358, 113.92898, 115.50214, 117.54028,
        };
        MSE instance = new MSE();
        double expResult = 0.80275;
        double result = instance.measure(truth, prediction);
        assertEquals(expResult, result, 1E-5);
    }
}