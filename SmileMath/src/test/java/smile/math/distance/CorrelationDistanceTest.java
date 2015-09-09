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
public class CorrelationDistanceTest {

    public CorrelationDistanceTest() {
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
     * Test of distance method, of class CorrelationDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        double[] x = {1.0, 2.0, 3.0, 4.0};
        double[] y = {4.0, 3.0, 2.0, 1.0};
        double[] z = {4.0, 2.0, 3.0, 1.0};
        double[] w = {-2.1968219, -0.9559913, -0.0431738,  1.0567679,  0.3853515};
        double[] v = {-1.7781325, -0.6659839,  0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.0, CorrelationDistance.pearson(x, x), 1E-5);
        assertEquals(2.0, CorrelationDistance.pearson(x, y), 1E-5);
        assertEquals(0.2, CorrelationDistance.pearson(y, z), 1E-5);
        assertEquals(0.5313153, CorrelationDistance.pearson(w, v), 1E-7);
    }
}