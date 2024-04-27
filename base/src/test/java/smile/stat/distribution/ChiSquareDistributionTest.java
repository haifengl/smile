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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ChiSquareDistributionTest {

    public ChiSquareDistributionTest() {
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
     * Test of length method, of class ChiSquareDistribution.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(1, instance.length());
    }

    /**
     * Test of mean method, of class ChiSquareDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(20, instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class ChiSquareDistribution.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(40, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class ChiSquareDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(Math.sqrt(40), instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class ChiSquareDistribution.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(3.229201359, instance.entropy(), 1E-7);
    }

    /**
     * Test of p method, of class ChiSquareDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(0.0, instance.p(0), 1E-7);
        assertEquals(2.559896e-18, instance.p(0.1), 1E-22);
        assertEquals(1.632262e-09, instance.p(1), 1E-15);
        assertEquals(0.01813279, instance.p(10), 1E-7);
        assertEquals(0.0625550, instance.p(20), 1E-7);
        assertEquals(7.2997e-05, instance.p(50), 1E-10);
        assertEquals(5.190544e-13, instance.p(100), 1E-18);
    }

    /**
     * Test of logP method, of class ChiSquareDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(Math.log(2.559896e-18), instance.logp(0.1), 1E-5);
        assertEquals(Math.log(1.632262e-09), instance.logp(1), 1E-5);
        assertEquals(Math.log(0.01813279), instance.logp(10), 1E-5);
        assertEquals(Math.log(0.0625550), instance.logp(20), 1E-5);
        assertEquals(Math.log(7.2997e-05), instance.logp(50), 1E-5);
        assertEquals(Math.log(5.190544e-13), instance.logp(100), 1E-5);
    }

    /**
     * Test of cdf method, of class ChiSquareDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(0.0, instance.cdf(0), 1E-7);
        assertEquals(2.571580e-20, instance.cdf(0.1), 1E-25);
        assertEquals(1.70967e-10, instance.cdf(1), 1E-15);
        assertEquals(0.03182806, instance.cdf(10), 1E-7);
        assertEquals(0.5420703, instance.cdf(20), 1E-7);
        assertEquals(0.9997785, instance.cdf(50), 1E-7);
        assertEquals(1.0, instance.cdf(100), 1E-7);
    }

    /**
     * Test of quantile method, of class ChiSquareDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        ChiSquareDistribution instance = new ChiSquareDistribution(20);
        instance.rand();
        assertEquals(0.0, instance.quantile(0), 1E-7);
        assertEquals(12.44261, instance.quantile(0.1), 1E-5);
        assertEquals(14.57844, instance.quantile(0.2), 1E-5);
        assertEquals(16.26586, instance.quantile(0.3), 1E-5);
        assertEquals(19.33743, instance.quantile(0.5), 1E-5);
        assertEquals(28.41198, instance.quantile(0.9), 1E-5);
    }
}