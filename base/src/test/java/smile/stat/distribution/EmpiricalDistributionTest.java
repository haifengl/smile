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

import smile.math.MathEx;
import smile.util.IntSet;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmpiricalDistribution.
 *
 * @author Haifeng Li
 */
public class EmpiricalDistributionTest {

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testUniformFit() {
        System.out.println("EmpiricalDistribution fit uniform");
        MathEx.setSeed(19650218);
        int[] data = {0, 1, 2, 3, 4, 0, 1, 2, 3, 4};
        EmpiricalDistribution dist = EmpiricalDistribution.fit(data);
        assertEquals(5, dist.p.length);
        for (double p : dist.p) {
            assertEquals(0.2, p, 1E-10);
        }
    }

    @Test
    public void testNonUniformFit() {
        System.out.println("EmpiricalDistribution fit non-uniform");
        int[] data = {0, 0, 0, 1, 1, 2};
        EmpiricalDistribution dist = EmpiricalDistribution.fit(data);
        assertEquals(0.5,  dist.p[0], 1E-10);
        assertEquals(1.0/3, dist.p[1], 1E-10);
        assertEquals(1.0/6, dist.p[2], 1E-10);
    }

    @Test
    public void testMean() {
        System.out.println("EmpiricalDistribution mean");
        // Uniform over {0,1,2,3,4} => mean = 2
        double[] probs = {0.2, 0.2, 0.2, 0.2, 0.2};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        assertEquals(2.0, dist.mean(), 1E-10);
    }

    @Test
    public void testVariance() {
        System.out.println("EmpiricalDistribution variance");
        // Uniform over {0,1,2,3,4} => variance = E[X^2] - (E[X])^2
        // E[X^2] = (0+1+4+9+16)/5 = 6, variance = 6 - 4 = 2
        double[] probs = {0.2, 0.2, 0.2, 0.2, 0.2};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        assertEquals(2.0, dist.variance(), 1E-10);
    }

    @Test
    public void testPmf() {
        System.out.println("EmpiricalDistribution p");
        double[] probs = {0.1, 0.4, 0.3, 0.2};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        assertEquals(0.1, dist.p(0), 1E-10);
        assertEquals(0.4, dist.p(1), 1E-10);
        assertEquals(0.3, dist.p(2), 1E-10);
        assertEquals(0.2, dist.p(3), 1E-10);
        assertEquals(0.0, dist.p(-1), 1E-10);
        assertEquals(0.0, dist.p(4), 1E-10);
    }

    @Test
    public void testLogPmf() {
        System.out.println("EmpiricalDistribution logp");
        double[] probs = {0.5, 0.5};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        assertEquals(Math.log(0.5), dist.logp(0), 1E-10);
        assertEquals(Math.log(0.5), dist.logp(1), 1E-10);
        assertTrue(Double.isInfinite(dist.logp(-1)));
    }

    @Test
    public void testCdf() {
        System.out.println("EmpiricalDistribution cdf");
        double[] probs = {0.2, 0.3, 0.5};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        assertEquals(0.0,  dist.cdf(-1), 1E-10);
        assertEquals(0.2,  dist.cdf(0),  1E-10);
        assertEquals(0.5,  dist.cdf(1),  1E-10);
        assertEquals(1.0,  dist.cdf(2),  1E-10);
    }

    @Test
    public void testQuantile() {
        System.out.println("EmpiricalDistribution quantile");
        double[] probs = {0.2, 0.3, 0.5};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        assertEquals(0, dist.quantile(0.1),  1E-10);
        assertEquals(1, dist.quantile(0.4),  1E-10);
        assertEquals(2, dist.quantile(0.9),  1E-10);
    }

    @Test
    public void testRand() {
        System.out.println("EmpiricalDistribution rand");
        MathEx.setSeed(19650218);
        // Degenerate distribution: all mass at 0
        double[] probs = {1.0};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        for (int i = 0; i < 100; i++) {
            assertEquals(0.0, dist.rand(), 1E-10);
        }
    }

    @Test
    public void testRandiDistribution() {
        System.out.println("EmpiricalDistribution randi distribution");
        MathEx.setSeed(19650218);
        double[] probs = {0.3, 0.7};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        int[] samples = dist.randi(10000);
        long zeros = 0;
        for (int s : samples) zeros += (s == 0 ? 1 : 0);
        double rate = zeros / 10000.0;
        assertEquals(0.3, rate, 0.02);
    }

    @Test
    public void testWithCustomValueSet() {
        System.out.println("EmpiricalDistribution custom values");
        int[] data = {10, 20, 10, 30, 20, 10};
        IntSet xs = IntSet.of(data);
        EmpiricalDistribution dist = EmpiricalDistribution.fit(data, xs);
        // 10 appears 3/6=0.5, 20 appears 2/6≈0.333, 30 appears 1/6≈0.167
        assertEquals(0.5,  dist.p(10), 1E-10);
        assertEquals(1.0/3, dist.p(20), 1E-10);
        assertEquals(1.0/6, dist.p(30), 1E-10);
    }

    @Test
    public void testLength() {
        System.out.println("EmpiricalDistribution length");
        double[] probs = {0.25, 0.25, 0.25, 0.25};
        EmpiricalDistribution dist = new EmpiricalDistribution(probs);
        assertEquals(4, dist.length());
    }

    @Test
    public void testInvalidProbabilities() {
        System.out.println("EmpiricalDistribution invalid probabilities");
        assertThrows(IllegalArgumentException.class, () -> {
            new EmpiricalDistribution(new double[]{0.5, 0.6}); // sum > 1
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new EmpiricalDistribution(new double[]{-0.1, 1.1}); // negative prob
        });
    }
}

