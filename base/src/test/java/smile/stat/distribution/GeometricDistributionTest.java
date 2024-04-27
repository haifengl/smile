/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.stat.distribution;

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GeometricDistributionTest {

    public GeometricDistributionTest() {
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
     * Test of constructor, of class GeometricDistribution.
     */
    @Test
    public void testGeometricDistribution() {
        System.out.println("GeometricDistribution");
        MathEx.setSeed(19650218); // to get repeatable results.
        GeometricDistribution instance = new GeometricDistribution(0.4);
        int[] data = instance.randi(1000);
        GeometricDistribution est = GeometricDistribution.fit(data);
        assertEquals(0.4, est.p, 1E-2);
    }

    /**
     * Test of length method, of class GeometricDistribution.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(1, instance.length());
    }

    /**
     * Test of mean method, of class GeometricDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(2.333333, instance.mean(), 1E-6);
    }

    /**
     * Test of variance method, of class GeometricDistribution.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(0.7/0.09, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class GeometricDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(2.788867, instance.sd(), 1E-6);
    }

    /**
     * Test of p method, of class GeometricDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(0.3, instance.p(0), 1E-6);
        assertEquals(0.21, instance.p(1), 1E-6);
        assertEquals(0.147, instance.p(2), 1E-6);
        assertEquals(0.1029, instance.p(3), 1E-6);
        assertEquals(0.07203, instance.p(4), 1E-6);
        assertEquals(0.008474257, instance.p(10), 1E-6);
        assertEquals(0.0002393768, instance.p(20), 1E-6);
    }

    /**
     * Test of logP method, of class GeometricDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(Math.log(0.3), instance.logp(0), 1E-6);
        assertEquals(Math.log(0.21), instance.logp(1), 1E-6);
        assertEquals(Math.log(0.147), instance.logp(2), 1E-6);
        assertEquals(Math.log(0.1029), instance.logp(3), 1E-6);
        assertEquals(Math.log(0.07203), instance.logp(4), 1E-6);
        assertEquals(Math.log(0.008474257), instance.logp(10), 1E-6);
        assertEquals(Math.log(0.0002393768), instance.logp(20), 1E-6);
    }

    /**
     * Test of cdf method, of class GeometricDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(0.3, instance.cdf(0), 1E-6);
        assertEquals(0.51, instance.cdf(1), 1E-6);
        assertEquals(0.657, instance.cdf(2), 1E-6);
        assertEquals(0.7599, instance.cdf(3), 1E-6);
        assertEquals(0.83193, instance.cdf(4), 1E-6);
        assertEquals(0.9802267, instance.cdf(10), 1E-6);
        assertEquals(0.9994415, instance.cdf(20), 1E-6);
    }

    /**
     * Test of quantile method, of class GeometricDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(0, instance.quantile(0.01), 1E-6);
        assertEquals(0, instance.quantile(0.1), 1E-6);
        assertEquals(0, instance.quantile(0.2), 1E-6);
        assertEquals(0, instance.quantile(0.3), 1E-6);
        assertEquals(1, instance.quantile(0.4), 1E-6);
        assertEquals(2, instance.quantile(0.6), 1E-6);
        assertEquals(4, instance.quantile(0.8), 1E-6);
        assertEquals(6, instance.quantile(0.9), 1E-6);
        assertEquals(12, instance.quantile(0.99), 1E-6);
    }
}