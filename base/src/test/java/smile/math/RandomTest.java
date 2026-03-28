/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class RandomTest {
    
    public RandomTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
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
