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

package smile.stat.hypothesis;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.stat.distribution.GaussianDistribution;

/**
 *
 * @author Haifeng Li
 */
public class KSTestTest {

    public KSTestTest() {
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
     * Test of test method, of class KSTest.
     */
    @Test
    public void testTest_doubleArr_Distribution() {
        System.out.println("test");
        double[] x = {
            0.53236606, -1.36750258, -1.47239199, -0.12517888, -1.24040594, 1.90357309,
            -0.54429527, 2.22084140, -1.17209146, -0.68824211, -1.75068914, 0.48505896,
            2.75342248, -0.90675303, -1.05971929, 0.49922388, -1.23214498, 0.79284888,
            0.85309580, 0.17903487, 0.39894754, -0.52744720, 0.08516943, -1.93817962,
            0.25042913, -0.56311389, -1.08608388, 0.11912253, 2.87961007, -0.72674865,
            1.11510699, 0.39970074, 0.50060532, -0.82531807, 0.14715616, -0.96133601,
            -0.95699473, -0.71471097, -0.50443258, 0.31690224, 0.04325009, 0.85316056,
            0.83602606, 1.46678847, 0.46891827, 0.69968175, 0.97864326, 0.66985742,
            -0.20922486, -0.15265994};
        KSTest result = KSTest.test(x, GaussianDistribution.getInstance());
        assertEquals(0.093, result.d, 1E-3);
        assertEquals(0.7598, result.pvalue, 1E-4);
        //assertEquals(0.7449, result.pvalue, 1E-4);
    }

    /**
     * Test of test method, of class KSTest.
     */
    @Test
    public void testTest_doubleArr_doubleArr() {
        System.out.println("test");
        double[] x = {
            0.53236606, -1.36750258, -1.47239199, -0.12517888, -1.24040594, 1.90357309,
            -0.54429527, 2.22084140, -1.17209146, -0.68824211, -1.75068914, 0.48505896,
            2.75342248, -0.90675303, -1.05971929, 0.49922388, -1.23214498, 0.79284888,
            0.85309580, 0.17903487, 0.39894754, -0.52744720, 0.08516943, -1.93817962,
            0.25042913, -0.56311389, -1.08608388, 0.11912253, 2.87961007, -0.72674865,
            1.11510699, 0.39970074, 0.50060532, -0.82531807, 0.14715616, -0.96133601,
            -0.95699473, -0.71471097, -0.50443258, 0.31690224, 0.04325009, 0.85316056,
            0.83602606, 1.46678847, 0.46891827, 0.69968175, 0.97864326, 0.66985742,
            -0.20922486, -0.15265994};
        double[] y = {
            0.95791391, 0.16203847, 0.56622013, 0.39252941, 0.99126354, 0.65639108,
            0.07903248, 0.84124582, 0.76718719, 0.80756577, 0.12263981, 0.84733360,
            0.85190907, 0.77896244, 0.84915723, 0.78225903, 0.95788055, 0.01849366,
            0.21000365, 0.97951772, 0.60078520, 0.80534223, 0.77144013, 0.28495121,
            0.41300867, 0.51547517, 0.78775718, 0.07564151, 0.82871088, 0.83988694};
        KSTest result = KSTest.test(x, y);
        assertEquals(0.46, result.d, 1E-2);
        assertEquals(0.00041647, result.pvalue, 1E-7);
    }
}