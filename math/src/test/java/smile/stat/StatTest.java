/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.stat;

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
public class StatTest {

    public StatTest() {
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
     * Test of GoodTuring method, of class Stat.
     */
    @Test
    public void testGoodTuring() {
        System.out.println("GoodTuring");
        int[] r = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12};
        int[] Nr = {120, 40, 24, 13, 15, 5, 11, 2, 2, 1, 3};
        double p0 = 0.2047782;
        double[] p = {
                0.0009267, 0.0024393, 0.0040945, 0.0058063, 0.0075464,
                0.0093026, 0.0110689, 0.0128418, 0.0146194, 0.0164005, 0.0199696};

        double[] result = new double[r.length];
        assertEquals(p0, Stat.GoodTuring(r, Nr, result), 1E-7);
        for (int i = 0; i < r.length; i++) {
            assertEquals(p[i], result[i], 1E-7);
        }
    }
}
