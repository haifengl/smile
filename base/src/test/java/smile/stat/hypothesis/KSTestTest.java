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
package smile.stat.hypothesis;

import smile.math.MathEx;
import smile.sort.IQAgent;
import smile.stat.Hypothesis;
import smile.stat.distribution.GaussianDistribution;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class KSTestTest {

    public KSTestTest() {
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
        KSTest test = KSTest.test(x, GaussianDistribution.getInstance());
        assertEquals(0.093, test.d(), 1E-3);
        assertEquals(0.7598, test.pvalue(), 1E-4);
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
        KSTest test = KSTest.test(x, y);
        assertEquals(0.46, test.d(), 1E-2);
        assertEquals(0.00041647, test.pvalue(), 1E-7);
    }

    @Test
    public void testStreamingOneSample() {
        System.out.println("streaming one-sample KS test");
        MathEx.setSeed(19650218);
        GaussianDistribution stdNormal = GaussianDistribution.getInstance();
        GaussianDistribution shifted = new GaussianDistribution(0.8, 1.0);

        IQAgent nullSketch = new IQAgent();
        IQAgent shiftedSketch = new IQAgent();
        for (int i = 0; i < 5000; i++) {
            nullSketch.add(stdNormal.rand());
            shiftedSketch.add(shifted.rand());
        }

        KSTest nullTest = Hypothesis.KS.test(nullSketch, stdNormal);
        assertTrue(nullTest.d() < 0.03, "Expected small KS d under H0, got " + nullTest.d());
        assertTrue(nullTest.pvalue() > 0.05, "Expected non-significant p-value under H0, got " + nullTest.pvalue());

        KSTest shiftedTest = Hypothesis.KS.test(shiftedSketch, stdNormal);
        assertTrue(shiftedTest.d() > 0.25, "Expected large KS d under shift, got " + shiftedTest.d());
        assertTrue(shiftedTest.pvalue() < 1e-10, "Expected tiny p-value under shift, got " + shiftedTest.pvalue());
    }

    @Test
    public void testStreamingTwoSampleAndHybrid() {
        System.out.println("streaming two-sample and hybrid KS test");
        MathEx.setSeed(19650218);
        GaussianDistribution dist1 = new GaussianDistribution(0.0, 1.0);
        GaussianDistribution dist2 = new GaussianDistribution(0.5, 1.0);

        double[] raw1 = new double[4000];
        double[] raw2 = new double[4000];
        IQAgent sketch1 = new IQAgent();
        IQAgent sketch2 = new IQAgent();
        for (int i = 0; i < 4000; i++) {
            raw1[i] = dist1.rand();
            raw2[i] = dist2.rand();
            sketch1.add(raw1[i]);
            sketch2.add(raw2[i]);
        }

        KSTest sketchVsSketch = Hypothesis.KS.test(sketch1, sketch2);
        KSTest hybrid = Hypothesis.KS.test(raw1, sketch2);
        KSTest exact = KSTest.test(raw1.clone(), raw2.clone());

        assertEquals(exact.d(), sketchVsSketch.d(), 0.02);
        assertEquals(exact.d(), hybrid.d(), 0.02);
        assertTrue(sketchVsSketch.pvalue() < 1e-10);
        assertTrue(hybrid.pvalue() < 1e-10);
    }
}