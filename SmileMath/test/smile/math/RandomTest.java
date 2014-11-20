/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.math;

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
public class RandomTest {
    
    public RandomTest() {
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
     * Test of random method, of class Random.
     */
    @Test
    public void testRandom() {
        System.out.println("random");
        smile.math.Random instance = new Random(System.currentTimeMillis());
        for (int i = 0; i < 1000000; i++) {
            double result = instance.nextDouble();
            assertTrue(result >= 0.0);
            assertTrue(result < 1.0);
        }
    }

    /**
     * Test of random method, of class Random.
     */
    @Test
    public void testRandom_double_double() {
        System.out.println("nextDouble");
        double lo = -10.0;
        double hi = 20.0;
        smile.math.Random instance = new Random(System.currentTimeMillis());
        for (int i = 0; i < 1000000; i++) {
            double result = instance.nextDouble(lo, hi);
            assertTrue(result >= lo);
            assertTrue(result < hi);
        }
    }

    /**
     * Test of randomInt method, of class Random.
     */
    @Test
    public void testRandomInt_int() {
        System.out.println("nextInt");
        smile.math.Random instance = new Random(System.currentTimeMillis());
        for (int i = 0; i < 1000000; i++) {
            int n = instance.nextInt(1000000) + 1;
            int result = instance.nextInt(n);
            assertTrue(result >= 0);
            assertTrue(result < n);
        }
    }

    /**
     * Test of randomInt method, of class Random.
     */
    @Test
    public void testRandomInt_int_int() {
        System.out.println("nextInt");
        int lo = -10;
        int hi = 20;
        smile.math.Random instance = new Random(System.currentTimeMillis());
        for (int i = 0; i < 1000000; i++) {
            double result = instance.nextDouble(lo, hi);
            assertTrue(result >= lo);
            assertTrue(result < hi);
        }
    }
}
