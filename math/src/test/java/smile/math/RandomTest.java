/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

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
