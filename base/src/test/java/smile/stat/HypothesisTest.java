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
package smile.stat;

import smile.stat.distribution.GaussianDistribution;
import smile.stat.hypothesis.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Hypothesis facade interface.
 *
 * @author Haifeng Li
 */
public class HypothesisTest {

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

    /**
     * Test significance code helper.
     */
    @Test
    public void testSignificance() {
        System.out.println("significance");
        assertEquals("***", Hypothesis.significance(0.0005));
        assertEquals("**",  Hypothesis.significance(0.005));
        assertEquals("*",   Hypothesis.significance(0.02));
        assertEquals(".",   Hypothesis.significance(0.07));
        assertEquals("",    Hypothesis.significance(0.5));
    }

    /**
     * Test t-test facade — one sample.
     */
    @Test
    public void testOneSampleT() {
        System.out.println("t-test one sample");
        // Samples centered very close to 2.0 — mean ≈ 2.0, should not reject H0
        double[] x = {1.9, 2.0, 2.1, 1.8, 2.2, 2.0, 1.9, 2.1, 2.0, 1.9, 2.1, 2.0};
        TTest result = Hypothesis.t.test(x, 2.0);
        assertNotNull(result);
        assertTrue(result.pvalue() > 0.05, "p-value=" + result.pvalue()); // fail to reject H0: mean=2.0
    }

    /**
     * Test t-test facade — two sample unequal variance.
     */
    @Test
    public void testTwoSampleT() {
        System.out.println("t-test two sample");
        double[] x = {2.5, 2.7, 2.3, 2.6, 2.4};
        double[] y = {3.0, 3.2, 3.1, 2.9, 3.0};
        TTest result = Hypothesis.t.test(x, y);
        assertNotNull(result);
        assertTrue(result.pvalue() < 0.05); // reject H0 at 5% level
    }

    /**
     * Test t-test facade with "equal.var" option.
     */
    @Test
    public void testTwoSampleTEqualVar() {
        System.out.println("t-test two sample equal.var");
        double[] x = {2.5, 2.7, 2.3, 2.6, 2.4};
        double[] y = {3.0, 3.2, 3.1, 2.9, 3.0};
        TTest result = Hypothesis.t.test(x, y, "equal.var");
        assertNotNull(result);
        assertTrue(result.pvalue() < 0.05);
    }

    /**
     * Test t-test facade with "paired" option.
     */
    @Test
    public void testPairedT() {
        System.out.println("t-test paired");
        double[] before = {5.0, 5.5, 4.8, 5.2, 5.1};
        double[] after  = {5.8, 6.0, 5.5, 5.7, 5.6};
        TTest result = Hypothesis.t.test(before, after, "paired");
        assertNotNull(result);
        assertTrue(result.pvalue() < 0.05); // significant difference
    }

    /**
     * Test t-test facade invalid option throws.
     */
    @Test
    public void testTInvalidOption() {
        assertThrows(IllegalArgumentException.class, () ->
                Hypothesis.t.test(new double[]{1,2}, new double[]{3,4}, "invalid"));
    }

    /**
     * Test F-test facade.
     */
    @Test
    public void testF() {
        System.out.println("F-test");
        double[] x = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] y = {1.1, 2.1, 2.9, 4.1, 4.9};
        FTest result = Hypothesis.F.test(x, y);
        assertNotNull(result);
        assertTrue(result.pvalue() > 0.05); // similar variances
    }

    /**
     * Test KS one-sample test facade.
     */
    @Test
    public void testKSOneSample() {
        System.out.println("KS one-sample test");
        smile.math.MathEx.setSeed(19650218);
        GaussianDistribution g = new GaussianDistribution(0, 1);
        double[] x = g.rand(200);
        KSTest result = Hypothesis.KS.test(x, g);
        assertNotNull(result);
        // Data drawn from the same distribution — p-value should be high
        assertTrue(result.pvalue() > 0.05);
    }

    /**
     * Test KS two-sample test facade.
     */
    @Test
    public void testKSTwoSample() {
        System.out.println("KS two-sample test");
        smile.math.MathEx.setSeed(19650218);
        GaussianDistribution g1 = new GaussianDistribution(0, 1);
        GaussianDistribution g2 = new GaussianDistribution(5, 1);
        double[] x = g1.rand(100);
        double[] y = g2.rand(100);
        KSTest result = Hypothesis.KS.test(x, y);
        assertNotNull(result);
        // Very different distributions — p-value should be very small
        assertTrue(result.pvalue() < 0.001);
    }

    /**
     * Test Pearson correlation test facade.
     */
    @Test
    public void testCorPearson() {
        System.out.println("Pearson correlation test");
        double[] x = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] y = {1.1, 2.1, 2.9, 4.2, 5.0, 5.9, 7.1, 8.0, 9.1, 10.0};
        CorTest result = Hypothesis.cor.test(x, y);
        assertNotNull(result);
        assertTrue(result.cor() > 0.99);
        assertTrue(result.pvalue() < 0.001);
    }

    /**
     * Test Spearman correlation test facade.
     */
    @Test
    public void testCorSpearman() {
        System.out.println("Spearman correlation test");
        double[] x = {1, 2, 3, 4, 5};
        double[] y = {2, 4, 6, 8, 10};
        CorTest result = Hypothesis.cor.test(x, y, "spearman");
        assertNotNull(result);
        assertEquals(1.0, result.cor(), 1E-6);
    }

    /**
     * Test Kendall correlation test facade.
     */
    @Test
    public void testCorKendall() {
        System.out.println("Kendall correlation test");
        double[] x = {1, 2, 3, 4, 5};
        double[] y = {2, 4, 6, 8, 10};
        CorTest result = Hypothesis.cor.test(x, y, "kendall");
        assertNotNull(result);
        assertEquals(1.0, result.cor(), 1E-6);
    }

    /**
     * Test correlation invalid method throws.
     */
    @Test
    public void testCorInvalidMethod() {
        assertThrows(IllegalArgumentException.class, () ->
                Hypothesis.cor.test(new double[]{1,2,3}, new double[]{1,2,3}, "invalid"));
    }

    /**
     * Test chi-square one-sample test facade.
     */
    @Test
    public void testChisqOneSample() {
        System.out.println("chi-square one-sample test");
        int[] bins = {20, 22, 13, 22, 10, 13};
        double[] prob = {1.0/6, 1.0/6, 1.0/6, 1.0/6, 1.0/6, 1.0/6};
        ChiSqTest result = Hypothesis.chisq.test(bins, prob);
        assertNotNull(result);
        assertEquals(8.36, result.chisq(), 1E-2);
    }

    /**
     * Test chi-square two-sample test facade.
     */
    @Test
    public void testChisqTwoSample() {
        System.out.println("chi-square two-sample test");
        int[] bins1 = {8, 13, 16, 10, 3};
        int[] bins2 = {4,  9, 14, 16, 7};
        ChiSqTest result = Hypothesis.chisq.test(bins1, bins2);
        assertNotNull(result);
        assertEquals(5.179, result.chisq(), 1E-2);
    }

    /**
     * Test chi-square contingency table test facade.
     */
    @Test
    public void testChisqTable() {
        System.out.println("chi-square contingency table test");
        int[][] table = {{12, 7}, {5, 7}};
        ChiSqTest result = Hypothesis.chisq.test(table);
        assertNotNull(result);
        assertEquals(0.4233, result.pvalue(), 1E-4);
    }
}

