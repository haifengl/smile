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

import java.util.List;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.math.MathEx;
import smile.sort.IQAgent;
import smile.stat.Hypothesis;
import smile.stat.distribution.GaussianDistribution;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PSI}.
 *
 * @author Haifeng Li
 */
public class PSITest {

    @Test
    public void testNumericSeverityLevels() {
        System.out.println("PSI numeric severity levels (STABLE, MODERATE, SIGNIFICANT)");
        MathEx.setSeed(19650218);

        GaussianDistribution baseDist = new GaussianDistribution(0.0, 1.0);
        GaussianDistribution moderateDist = new GaussianDistribution(0.42, 1.0);
        GaussianDistribution severeDist = new GaussianDistribution(1.0, 1.0);

        int n = 10000;
        double[] baseline = new double[n];
        double[] sameWindow = new double[n];
        double[] moderateWindow = new double[n];
        double[] severeWindow = new double[n];

        for (int i = 0; i < n; i++) {
            baseline[i] = baseDist.rand();
            sameWindow[i] = baseDist.rand();
            moderateWindow[i] = moderateDist.rand();
            severeWindow[i] = severeDist.rand();
        }

        PSI stable = Hypothesis.psi.test(baseline, sameWindow);
        assertEquals(PSI.Severity.STABLE, stable.severity());
        assertTrue(stable.psi() < PSI.MODERATE_THRESHOLD, "Expected PSI < 0.10, got " + stable.psi());
        assertTrue(stable.tvd() < 0.05);
        assertEquals(10, stable.binPsi().length);
        assertEquals(9, stable.breaks().length);

        PSI moderate = Hypothesis.psi.test(baseline, moderateWindow);
        assertEquals(PSI.Severity.MODERATE, moderate.severity());
        assertTrue(moderate.psi() >= PSI.MODERATE_THRESHOLD && moderate.psi() < PSI.SIGNIFICANT_THRESHOLD,
                "Expected 0.10 <= PSI < 0.25, got " + moderate.psi());

        PSI severe = Hypothesis.psi.test(baseline, severeWindow);
        assertEquals(PSI.Severity.SIGNIFICANT, severe.severity());
        assertTrue(severe.psi() >= PSI.SIGNIFICANT_THRESHOLD, "Expected PSI >= 0.25, got " + severe.psi());
        assertTrue(severe.chisq().pvalue() < 1e-20);
        assertTrue(stable.compareTo(severe) < 0);
    }

    @Test
    public void testOutOfRangeTailsAndTiedQuantiles() {
        System.out.println("PSI out-of-range tails and tied quantile breakpoints");
        // Zero-inflated baseline: 60% zeros, 40% positive values in [1, 400]
        double[] baseline = new double[1000];
        for (int i = 600; i < 1000; i++) {
            baseline[i] = i - 599;
        }

        // Actual window with extreme negative and positive tail values outside baseline range
        double[] actual = new double[1000];
        for (int i = 0; i < 500; i++) {
            actual[i] = -100.0; // below baseline min (0.0)
        }
        for (int i = 500; i < 1000; i++) {
            actual[i] = 10000.0; // above baseline max (400.0)
        }

        PSI result = PSI.test("zero_inflated", baseline, actual, 10);
        assertTrue(Double.isFinite(result.psi()));
        assertEquals(1000L, result.actualCount());
        assertEquals(PSI.Severity.SIGNIFICANT, result.severity());
        // Interior breakpoints must be strictly increasing despite 60% zeros in baseline
        double[] breaks = result.breaks();
        for (int i = 1; i < breaks.length; i++) {
            assertTrue(breaks[i] > breaks[i - 1]);
        }
    }

    @Test
    public void testCategoricalFrequencyShiftAndUnseenCategory() {
        System.out.println("PSI categorical frequency shift and unseen category handling");
        int[] expected = new int[1000];
        int[] actualStable = new int[500];
        int[] actualDrifted = new int[500];

        // Baseline: 50% cat 0, 30% cat 1, 20% cat 2
        for (int i = 0; i < 1000; i++) {
            expected[i] = (i < 500) ? 0 : (i < 800 ? 1 : 2);
        }
        for (int i = 0; i < 500; i++) {
            actualStable[i] = (i < 250) ? 0 : (i < 400 ? 1 : 2);
            actualDrifted[i] = (i < 100) ? 0 : (i < 200 ? 1 : 2);
        }

        PSI stable = Hypothesis.psi.testCategorical(expected, actualStable);
        assertEquals(0.0, stable.psi(), 1e-12);
        assertEquals(0.0, stable.tvd(), 1e-12);
        assertEquals(PSI.Severity.STABLE, stable.severity());
        assertNull(stable.breaks());

        PSI drifted = Hypothesis.psi.testCategorical(expected, actualDrifted);
        assertEquals(PSI.Severity.SIGNIFICANT, drifted.severity());
        assertEquals(0.40, drifted.tvd(), 1e-9);
        assertTrue(drifted.chisq().pvalue() < 1e-15);

        // Streaming counter with unseen out-of-vocabulary category code (code 5)
        PSI.Baseline catBaseline = PSI.Baseline.categorical("segment", expected, 3);
        PSI.Counter counter = catBaseline.newCounter();
        counter.addCategorical(actualStable);
        for (int i = 0; i < 50; i++) {
            counter.addCategorical(5); // unseen category
        }
        assertEquals(50L, counter.unseenCount());
        PSI withUnseen = counter.test();
        assertTrue(Double.isFinite(withUnseen.psi()));
        assertTrue(withUnseen.psi() > 0.05);
        assertEquals(4, withUnseen.actualProb().length);
    }

    @Test
    public void testStreamingCounterMergeResetAndIQAgentBaseline() {
        System.out.println("PSI streaming Counter merge, reset, and IQAgent baseline");
        MathEx.setSeed(19650218);
        GaussianDistribution dist = new GaussianDistribution(0.0, 1.0);
        IQAgent baselineSketch = new IQAgent();
        for (int i = 0; i < 20000; i++) {
            baselineSketch.add(dist.rand());
        }

        PSI.Baseline baseline = PSI.Baseline.numeric("latency_ms", baselineSketch, 10);
        PSI.Counter c1 = baseline.newCounter();
        PSI.Counter c2 = baseline.newCounter();

        for (int i = 0; i < 2000; i++) {
            c1.add(dist.rand());
            c2.add(dist.rand());
        }
        c1.merge(c2);
        assertEquals(4000L, c1.count());

        PSI mergedResult = c1.test();
        assertEquals(PSI.Severity.STABLE, mergedResult.severity());

        c1.reset();
        assertEquals(0L, c1.count());
        assertThrows(IllegalStateException.class, c1::test);
    }

    @Test
    public void testDataFrameMultiFeatureDrift() {
        System.out.println("PSI DataFrame multi-feature drift");
        MathEx.setSeed(19650218);
        int n = 2000;
        double[] numBase = new double[n];
        double[] numDrift = new double[n];
        int[] catBase = new int[n];
        int[] catStable = new int[n];

        GaussianDistribution n0 = new GaussianDistribution(0.0, 1.0);
        GaussianDistribution n1 = new GaussianDistribution(1.2, 1.0);
        for (int i = 0; i < n; i++) {
            numBase[i] = n0.rand();
            numDrift[i] = n1.rand();
            catBase[i] = i % 3;
            catStable[i] = (i + 1) % 3;
        }

        NominalScale scale = new NominalScale("A", "B", "C");
        StructType schema = new StructType(
                new StructField("score", DataTypes.DoubleType),
                new StructField("tier", DataTypes.IntType, scale)
        );

        DataFrame baseDf = new DataFrame(
                new DoubleVector("score", numBase),
                new IntVector(new StructField("tier", DataTypes.IntType, scale), catBase)
        );
        DataFrame prodDf = new DataFrame(
                new DoubleVector("score", numDrift),
                new IntVector(new StructField("tier", DataTypes.IntType, scale), catStable)
        );

        assertEquals(schema, baseDf.schema());
        PSI[] results = Hypothesis.psi.test(baseDf, prodDf);
        assertEquals(2, results.length);
        assertEquals("score", results[0].feature());
        assertEquals(PSI.Severity.SIGNIFICANT, results[0].severity());
        assertEquals("tier", results[1].feature());
        assertEquals(PSI.Severity.STABLE, results[1].severity());

        String formatted = PSI.toString(results);
        assertTrue(formatted.contains("score"));
        assertTrue(formatted.contains("SIGNIFICANT"));
    }
}
