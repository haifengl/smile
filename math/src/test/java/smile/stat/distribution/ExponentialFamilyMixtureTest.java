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

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Haifeng Li
 */
public class ExponentialFamilyMixtureTest {

    public ExponentialFamilyMixtureTest() {
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
     * Test of EM method, of class ExponentialFamilyMixture.
     * The main purpose is to test the speed.
     */
    @Test
    public void testEM() {
        System.out.println("EM");

        // Mixture of Gaussian, Exponential, and Gamma.
        double[] data = new double[2000];

        GaussianDistribution gaussian = new GaussianDistribution(-2.0, 1.0);
        for (int i = 0; i < 500; i++)
            data[i] = gaussian.rand();

        ExponentialDistribution exponential = new ExponentialDistribution(0.8);
        for (int i = 500; i < 1000; i++)
            data[i] = exponential.rand();

        GammaDistribution gamma = new GammaDistribution(2.0, 3.0);
        for (int i = 1000; i < 2000; i++)
            data[i] = gamma.rand();

        List<Mixture.Component> m = new ArrayList<>();
        Mixture.Component c = new Mixture.Component();
        c.priori = 0.25;
        c.distribution = new GaussianDistribution(0.0, 1.0);
        m.add(c);

        c = new Mixture.Component();
        c.priori = 0.25;
        c.distribution = new ExponentialDistribution(1.0);
        m.add(c);

        c = new Mixture.Component();
        c.priori = 0.5;
        c.distribution = new GammaDistribution(1.0, 2.0);
        m.add(c);

        ExponentialFamilyMixture mixture = new ExponentialFamilyMixture(m, data);
        System.out.println(mixture);
    }
}