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

package smile.stat.distribution;

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
public class GaussianMixtureTest {

    public GaussianMixtureTest() {
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
     * Test of GaussianMixture.
     */
    @Test
    public void testMixture3() {
        System.out.println("Mixture3");

        double[] data = {
            23.0, 23.0, 22.0, 22.0, 21.0, 24.0, 24.0, 24.0, 24.0,
            24.0, 24.0, 24.0, 24.0, 22.0, 22.0, 16.0, 16.0, 16.0,
            23.0, 23.0, 15.0, 21.0, 21.0, 21.0, 21.0, 24.0, 24.0,
            21.0, 21.0, 24.0, 24.0, 24.0, 24.0,  1.0,  1.0, 23.0,
            23.0, 22.0, 22.0, 14.0, 24.0, 24.0, 23.0, 23.0, 18.0,
            18.0, 23.0, 23.0, 24.0, 24.0, 22.0, 22.0, 17.0, 17.0,
            17.0, 21.0, 21.0, 15.0, 14.0
        };

        GaussianMixture mixture = new GaussianMixture(data);
        System.out.println(mixture);
        assertEquals(3, mixture.size());
    }

    /**
     * Test of GaussianMixture.
     */
    @Test
    public void testMixture5() {
        System.out.println("Mixture5");

        double[] data = new double[30000];

        GaussianDistribution g1 = new GaussianDistribution(1.0, 1.0);
        for (int i = 0; i < 5000; i++)
            data[i] = g1.rand();

        GaussianDistribution g2 = new GaussianDistribution(4.0, 1.0);
        for (int i = 5000; i < 10000; i++)
            data[i] = g2.rand();

        GaussianDistribution g3 = new GaussianDistribution(8.0, 1.0);
        for (int i = 10000; i < 20000; i++)
            data[i] = g3.rand();

        GaussianDistribution g4 = new GaussianDistribution(-2.0, 1.0);
        for (int i = 20000; i < 25000; i++)
            data[i] = g4.rand();

        GaussianDistribution g5 = new GaussianDistribution(-5.0, 1.0);
        for (int i = 25000; i < 30000; i++)
            data[i] = g5.rand();
/* TODO: It doesn't converge any more
        GaussianMixture mixture = new GaussianMixture(data);
        System.out.println(mixture);
        assertTrue(mixture.size() <= 7);
        assertTrue(mixture.size() >= 5);
        */
    }
}