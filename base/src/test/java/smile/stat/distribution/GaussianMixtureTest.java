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
package smile.stat.distribution;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GaussianMixtureTest {

    public GaussianMixtureTest() {
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

        GaussianMixture mixture = GaussianMixture.fit(data);
        System.out.println(mixture);
        assertEquals(3, mixture.size());
    }

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

        GaussianMixture mixture = GaussianMixture.fit(data);
        System.out.println(mixture);
    }

    /**
     * Test that Mixture.variance() uses the law of total variance:
     * Var(X) = E[Var(X|Z)] + Var(E[X|Z])
     *        = sum(w_i * sigma_i^2) + sum(w_i * mu_i^2) - mu^2
     *
     * For a 50/50 mixture of N(0,1) and N(4,1):
     *   mu  = 0.5*0 + 0.5*4 = 2
     *   var = 0.5*(1 + 0^2) + 0.5*(1 + 4^2) - 2^2
     *       = 0.5*(1+0) + 0.5*(1+16) - 4
     *       = 0.5 + 8.5 - 4 = 5.0
     */
    @Test
    public void testMixtureVariance() {
        System.out.println("Mixture variance (law of total variance)");
        Mixture.Component c1 = new Mixture.Component(0.5, new GaussianDistribution(0.0, 1.0));
        Mixture.Component c2 = new Mixture.Component(0.5, new GaussianDistribution(4.0, 1.0));
        Mixture mixture = new Mixture(c1, c2);

        assertEquals(2.0, mixture.mean(), 1E-10);
        assertEquals(5.0, mixture.variance(), 1E-10);
    }
}

