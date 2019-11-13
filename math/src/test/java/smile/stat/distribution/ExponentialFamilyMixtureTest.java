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

package smile.stat.distribution;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import static org.junit.Assert.*;

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
        MathEx.setSeed(19650218); // to get repeatable results.

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

        ExponentialFamilyMixture mixture = ExponentialFamilyMixture.fit(data,
                new Mixture.Component(0.25, new GaussianDistribution(0.0, 1.0)),
                new Mixture.Component(0.25, new ExponentialDistribution(1.0)),
                new Mixture.Component(0.5, new GammaDistribution(1.0, 2.0))
                );
        System.out.println(mixture);

        assertEquals(0.30, mixture.components[0].priori, 1E-2);
        assertEquals(0.13, mixture.components[1].priori, 1E-2);
        assertEquals(0.57, mixture.components[2].priori, 1E-2);
    }
}