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

package smile.interpolation;

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
public class LaplaceInterpolationTest {

    public LaplaceInterpolationTest() {
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
     * Test of interpolate method, of class LaplaceInterpolation.
     */
    @Test
    public void testInterpolate() {
        System.out.println("interpolate");
        double[][] matrix = {{0, Double.NaN}, {1, 2}};
        double error = LaplaceInterpolation.interpolate(matrix);
        assertEquals(0, matrix[0][0], 1E-7);
        assertEquals(1, matrix[1][0], 1E-7);
        assertEquals(2, matrix[1][1], 1E-7);
        assertEquals(1, matrix[0][1], 1E-7);
        assertTrue(error < 1E-6);
    }
}