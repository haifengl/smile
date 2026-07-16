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
package smile.sort;

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IQAgent}.
 *
 * @author Haifeng Li
 */
public class IQAgentTest {

    // -----------------------------------------------------------------------
    // Basic accuracy against a known uniform [1..100000] distribution
    // -----------------------------------------------------------------------

    @Test
    public void testQuantileAccuracy() {
        System.out.println("IQAgent quantile accuracy");
        double[] data = new double[100000];
        for (int i = 0; i < data.length; i++) data[i] = i + 1;
        MathEx.permutate(data);

        IQAgent agent = new IQAgent();
        for (double d : data) agent.add(d);

        for (int i = 1; i <= 100; i++) {
            double q = agent.quantile(i / 100.0);
            double expected = i * 1000.0;
            assertTrue(Math.abs(1 - q / expected) < 0.01,
                    String.format("p=%.2f: expected ~%.0f, got %.2f", i / 100.0, expected, q));
        }
    }

    // -----------------------------------------------------------------------
    // Quantile boundary values
    // -----------------------------------------------------------------------

    @Test
    public void testQuantileMin() {
        System.out.println("IQAgent quantile(0) ≈ min");
        IQAgent agent = new IQAgent();
        for (int i = 1; i <= 1000; i++) agent.add(i);
        // p=0 should be close to the minimum (1)
        double q0 = agent.quantile(0.0);
        assertTrue(q0 >= 1.0 && q0 <= 10.0,
                "quantile(0) should be near min; got " + q0);
    }

    @Test
    public void testQuantileMax() {
        System.out.println("IQAgent quantile(1) ≈ max");
        IQAgent agent = new IQAgent();
        for (int i = 1; i <= 1000; i++) agent.add(i);
        double q1 = agent.quantile(1.0);
        assertTrue(q1 >= 990.0 && q1 <= 1000.0,
                "quantile(1) should be near max; got " + q1);
    }

    @Test
    public void testQuantileMedian() {
        System.out.println("IQAgent quantile(0.5) ≈ median");
        IQAgent agent = new IQAgent();
        for (int i = 1; i <= 10000; i++) agent.add(i);
        double med = agent.quantile(0.5);
        // true median of [1..10000] is 5000.5
        assertTrue(Math.abs(med - 5000.5) / 5000.5 < 0.01,
                "Median should be ~5000; got " + med);
    }

    // -----------------------------------------------------------------------
    // Monotonicity: quantile(p) must be non-decreasing in p
    // -----------------------------------------------------------------------

    @Test
    public void testQuantileMonotonicity() {
        System.out.println("IQAgent quantile monotonicity");
        IQAgent agent = new IQAgent();
        for (int i = 0; i < 50000; i++) agent.add(MathEx.random());

        double prev = agent.quantile(0.01);
        for (int i = 2; i <= 99; i++) {
            double q = agent.quantile(i / 100.0);
            assertTrue(q >= prev - 1e-9,
                    String.format("Non-monotone: q(%.2f)=%.6f < q(%.2f)=%.6f",
                            i / 100.0, q, (i - 1) / 100.0, prev));
            prev = q;
        }
    }

    // -----------------------------------------------------------------------
    // Custom batch size constructor
    // -----------------------------------------------------------------------

    @Test
    public void testCustomBatchSize() {
        System.out.println("IQAgent custom batch size");
        IQAgent agent = new IQAgent(200);   // small batch to stress flush path
        for (int i = 1; i <= 10000; i++) agent.add(i);
        double med = agent.quantile(0.5);
        assertTrue(Math.abs(med - 5000.5) / 5000.5 < 0.02,
                "Custom-batch median should be ~5000; got " + med);
    }

    @Test
    public void testLargeBatchSize() {
        System.out.println("IQAgent large batch size (10000)");
        IQAgent agent = new IQAgent(10000);
        double[] data = new double[100000];
        for (int i = 0; i < data.length; i++) data[i] = i + 1;
        MathEx.permutate(data);
        for (double d : data) agent.add(d);

        double q25 = agent.quantile(0.25);
        double q75 = agent.quantile(0.75);
        assertTrue(Math.abs(q25 - 25000) / 25000 < 0.02,
                "q1 should be ~25000; got " + q25);
        assertTrue(Math.abs(q75 - 75000) / 75000 < 0.02,
                "q3 should be ~75000; got " + q75);
    }

    // -----------------------------------------------------------------------
    // Constant stream (all same value)
    // -----------------------------------------------------------------------

    @Test
    public void testConstantStream() {
        System.out.println("IQAgent constant stream");
        IQAgent agent = new IQAgent();
        for (int i = 0; i < 5000; i++) agent.add(7.0);
        // All quantiles should be 7
        for (double p : new double[]{0.1, 0.25, 0.5, 0.75, 0.9}) {
            assertEquals(7.0, agent.quantile(p), 0.5,
                    "Constant stream: quantile(" + p + ") should be 7");
        }
    }

    // -----------------------------------------------------------------------
    // Q1 < median < Q3 ordering
    // -----------------------------------------------------------------------

    @Test
    public void testQuantileOrdering() {
        System.out.println("IQAgent q1 < median < q3");
        IQAgent agent = new IQAgent();
        for (int i = 0; i < 10000; i++) agent.add(MathEx.random());

        double q1  = agent.quantile(0.25);
        double med = agent.quantile(0.50);
        double q3  = agent.quantile(0.75);
        assertTrue(q1 <= med, "q1 should be <= median");
        assertTrue(med <= q3, "median should be <= q3");
    }

    // -----------------------------------------------------------------------
    // Incremental add flushes correctly
    // -----------------------------------------------------------------------

    @Test
    public void testIncrementalAdd() {
        System.out.println("IQAgent incremental add (triggers multiple batch flushes)");
        // Use batch=100 so we trigger many flushes
        IQAgent agent = new IQAgent(100);
        for (int i = 1; i <= 5000; i++) agent.add(i);
        double med = agent.quantile(0.5);
        assertTrue(Math.abs(med - 2500.5) / 2500.5 < 0.02,
                "Incremental median should be ~2500; got " + med);
    }
}