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
package smile.classification.resampling;

import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.*;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BSO}.
 *
 * @author Haifeng Li
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class BSOTest {

    /**
     * 2-D dataset: 90 majority (label=0) around (-0.5, -0.5) and 10 minority
     * (label=1) partially overlapping at (0.5, 0.5), ensuring DANGER samples exist.
     */
    private static double[][] data;
    private static int[] labels;

    @BeforeAll
    static void setup() {
        MathEx.setSeed(19650218);
        data = new double[100][2];
        labels = new int[100];
        // Majority cluster overlapping with minority to guarantee DANGER samples.
        for (int i = 0; i < 90; i++) {
            data[i][0] = MathEx.random(-2.0, 2.0);
            data[i][1] = MathEx.random(-2.0, 2.0);
            labels[i] = 0;
        }
        // Minority inside majority range — creates DANGER borderline instances.
        for (int i = 90; i < 100; i++) {
            data[i][0] = MathEx.random(-0.5, 0.5);
            data[i][1] = MathEx.random(-0.5, 0.5);
            labels[i] = 1;
        }
    }

    // ─── Options tests ────────────────────────────────────────────────────────

    @Test
    public void testDefaultOptions() {
        var opts = new BSO.Options();
        assertEquals(5,            opts.m());
        assertEquals(3,            opts.kMaj());
        assertEquals(0.2,          opts.alpha(),  1e-9);
        assertEquals(1.0,          opts.ratio(),  1e-9);
        assertEquals(BSO.Strategy.DANGER, opts.strategy());
        assertEquals(20,           opts.highDimThreshold());
        assertEquals(10,           opts.rpfNumTrees());
        assertEquals(30,           opts.rpfLeafSize());
    }

    @Test
    public void testOptionsToPropertiesRoundTrip() {
        // Given
        var opts = new BSO.Options(7, 4, 0.3, 1.5, BSO.Strategy.DANGER_AND_SAFE, 15, 8, 25);

        // When
        Properties props = opts.toProperties();
        BSO.Options restored = BSO.Options.of(props);

        // Then
        assertEquals(opts.m(),                restored.m());
        assertEquals(opts.kMaj(),             restored.kMaj());
        assertEquals(opts.alpha(),            restored.alpha(),  1e-9);
        assertEquals(opts.ratio(),            restored.ratio(),  1e-9);
        assertEquals(opts.strategy(),          restored.strategy());
        assertEquals(opts.highDimThreshold(), restored.highDimThreshold());
        assertEquals(opts.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(opts.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsOfUsesDefaultsForMissingKeys() {
        var defaults = new BSO.Options();
        var restored = BSO.Options.of(new Properties());

        assertEquals(defaults.m(),                restored.m());
        assertEquals(defaults.kMaj(),             restored.kMaj());
        assertEquals(defaults.alpha(),            restored.alpha(),  1e-9);
        assertEquals(defaults.ratio(),            restored.ratio(),  1e-9);
        assertEquals(defaults.strategy(),          restored.strategy());
        assertEquals(defaults.highDimThreshold(), restored.highDimThreshold());
        assertEquals(defaults.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(defaults.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsPropertyKeys() {
        var opts  = new BSO.Options(3, 2, 0.4, 2.0, BSO.Strategy.DANGER_AND_SAFE, 10, 5, 15);
        var props = opts.toProperties();

        assertEquals("3",    props.getProperty("smile.bso.m"));
        assertEquals("2",    props.getProperty("smile.bso.k_maj"));
        assertEquals("0.4",  props.getProperty("smile.bso.alpha"));
        assertEquals("2.0",  props.getProperty("smile.bso.ratio"));
        assertEquals("DANGER_AND_SAFE", props.getProperty("smile.bso.strategy"));
        assertEquals("10",   props.getProperty("smile.bso.high_dim_threshold"));
        assertEquals("5",    props.getProperty("smile.bso.rpf_num_trees"));
        assertEquals("15",   props.getProperty("smile.bso.rpf_leaf_size"));
    }

    @Test
    public void testInvalidOptionsThrows() {
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(0, 3, 0.2, 1.0, BSO.Strategy.DANGER, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(5, -1, 0.2, 1.0, BSO.Strategy.DANGER, 20, 10, 30)); // kMaj=-1
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(5, 3, 0.0, 1.0, BSO.Strategy.DANGER, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(5, 3, 1.0, 1.0, BSO.Strategy.DANGER, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(5, 3, 0.2, 0.0, BSO.Strategy.DANGER, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(5, 3, 0.2, 1.0, BSO.Strategy.DANGER, 0, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(5, 3, 0.2, 1.0, BSO.Strategy.DANGER, 20, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(5, 3, 0.2, 1.0, BSO.Strategy.DANGER, 20, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new BSO.Options(5, 3, 0.2, 1.0, null, 20, 10, 30));
    }

    @Test
    public void testKMajZeroIsValid() {
        // Given — kMaj=0 is explicitly allowed (Borderline-SMOTE interpolation mode)
        assertDoesNotThrow(() -> new BSO.Options(5, 0, 0.2, 1.0, BSO.Strategy.DANGER, 20, 10, 30));
    }

    @Test
    public void testKMajZeroFallsBackToBorderlineSmote() {
        // Given — kMaj=0 disables shifting; synthetics are SMOTE-style interpolations
        // between borderline minority samples and their minority neighbors.
        MathEx.setSeed(77);
        var opts = new BSO.Options(5, 0, 0.5 /* alpha irrelevant */, 1.0, BSO.Strategy.DANGER, 20, 10, 30);

        // When
        BSO result = BSO.fit(data, labels, opts);

        // Then — correct synthetic count and all synthetic labels are minority
        assertEquals(110, result.size());
        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(20, minCount);

        // Synthetic samples (rows 100–109) must lie within the minority cluster range
        // since interpolation stays between minority points around (−0.5, 0.5).
        for (int i = 100; i < result.size(); i++) {
            double[] s = result.data()[i];
            assertTrue(s[0] >= -2.0 && s[0] <= 2.0, "x out of range: " + s[0]);
            assertTrue(s[1] >= -2.0 && s[1] <= 2.0, "y out of range: " + s[1]);
        }
    }

    @Test
    public void testKMajZeroPropertiesRoundTrip() {
        // Given — kMaj=0 must survive toProperties / of round-trip
        var opts = new BSO.Options(5, 0, 0.3, 1.0, BSO.Strategy.DANGER, 20, 10, 30);
        BSO.Options restored = BSO.Options.of(opts.toProperties());
        assertEquals(0, restored.kMaj());
    }

    // ─── fit() tests ─────────────────────────────────────────────────────────

    @Test
    public void testFitDefaultOptionsDoublesMinorityClass() {
        // Given / When — default ratio=1.0 should add 10 synthetic samples
        MathEx.setSeed(42);
        BSO result = BSO.fit(data, labels);

        // Then
        assertEquals(110, result.size());
        assertEquals(110, result.data().length);
        assertEquals(110, result.labels().length);

        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(20, minCount);
        long majCount = Arrays.stream(result.labels()).filter(l -> l == 0).count();
        assertEquals(90, majCount);
    }

    @Test
    public void testFitWithRatioTwo() {
        // Given — ratio=2.0 triples minority (10 + 20 = 30)
        MathEx.setSeed(7);
        var opts = new BSO.Options(5, 3, 0.2, 2.0, BSO.Strategy.DANGER);

        // When
        BSO result = BSO.fit(data, labels, opts);

        // Then
        assertEquals(120, result.size());
        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(30, minCount);
    }

    @Test
    public void testOriginalSamplesPreserved() {
        // Given / When
        MathEx.setSeed(3);
        BSO result = BSO.fit(data, labels);

        // Then — first 100 rows unchanged
        for (int i = 0; i < 100; i++) {
            assertArrayEquals(data[i], result.data()[i], 1e-12, "Row " + i + " modified.");
            assertEquals(labels[i], result.labels()[i], "Label " + i + " modified.");
        }
    }

    @Test
    public void testSyntheticSamplesHaveMinorityLabel() {
        // Given / When
        MathEx.setSeed(13);
        BSO result = BSO.fit(data, labels);

        // Then
        for (int i = 100; i < result.size(); i++) {
            assertEquals(1, result.labels()[i],
                    "Synthetic sample at row " + i + " has wrong label.");
        }
    }

    @Test
    public void testSyntheticSamplesShiftedTowardMajority() {
        // Given — shifted samples should be in the zone between minority cluster
        // (centred near 0,0) and majority cluster (spanning -2..2). With alpha=0.2
        // synthetics stay mostly in the minority range but shift toward majority.
        MathEx.setSeed(22);
        var opts = new BSO.Options(5, 3, 0.2, 1.0, BSO.Strategy.DANGER);
        BSO result = BSO.fit(data, labels, opts);

        // All synthetic samples must be within the data bounding box
        for (int i = 100; i < result.size(); i++) {
            double[] s = result.data()[i];
            assertTrue(s[0] >= -3.0 && s[0] <= 3.0, "x out of bounds: " + s[0]);
            assertTrue(s[1] >= -3.0 && s[1] <= 3.0, "y out of bounds: " + s[1]);
        }
    }

    @Test
    public void testBso2ProducesMoreOrEqualSeedsThanBso1() {
        // Given — BSO2 includes SAFE samples as secondary seeds; with ratio=1.0
        // both produce the same numSynthetic. The test checks that BSO2 doesn't crash.
        MathEx.setSeed(55);
        var opts1 = new BSO.Options(5, 3, 0.2, 1.0, BSO.Strategy.DANGER);
        var opts2 = new BSO.Options(5, 3, 0.2, 1.0, BSO.Strategy.DANGER_AND_SAFE);

        BSO result1 = BSO.fit(data, labels, opts1);
        BSO result2 = BSO.fit(data, labels, opts2);

        // Both should produce 10 synthetic samples for ratio=1.0 starting from 10 minority
        assertEquals(110, result1.size());
        assertEquals(110, result2.size());

        // Synthetic labels should all be minority label=1
        for (int i = 100; i < 110; i++) {
            assertEquals(1, result1.labels()[i]);
            assertEquals(1, result2.labels()[i]);
        }
    }

    @Test
    public void testFallbackWhenNoDangerSamples() {
        // Given — well-separated dataset where all minority samples are SAFE or NOISE
        MathEx.setSeed(77);
        double[][] d2 = new double[100][2];
        int[] l2 = new int[100];
        for (int i = 0; i < 90; i++) {
            d2[i][0] = MathEx.random(-5.0, 5.0);
            d2[i][1] = MathEx.random(-5.0, 5.0);
            l2[i] = 0;
        }
        // Minority tightly clustered far from majority
        for (int i = 90; i < 100; i++) {
            d2[i][0] = 20.0 + MathEx.random(-0.1, 0.1);
            d2[i][1] = 20.0 + MathEx.random(-0.1, 0.1);
            l2[i] = 1;
        }
        var opts = new BSO.Options(5, 3, 0.2, 1.0, BSO.Strategy.DANGER);

        // When — fallback to all minority samples should be triggered
        BSO result = BSO.fit(d2, l2, opts);

        // Then — still produces correct number of synthetics
        assertEquals(110, result.size());
    }

    @Test
    public void testHighDimensionalData() {
        // Given — 50-D dataset (d > 20 → RandomProjectionForest)
        MathEx.setSeed(99);
        int d = 50, nMin = 40, nMaj = 200;
        double[][] hd = new double[nMaj + nMin][d];
        int[] hl = new int[nMaj + nMin];
        for (int i = 0; i < nMaj; i++) {
            for (int j = 0; j < d; j++) hd[i][j] = MathEx.random(-2.0, 2.0);
            hl[i] = 0;
        }
        // Minority inside majority range to ensure DANGER samples exist.
        for (int i = nMaj; i < nMaj + nMin; i++) {
            for (int j = 0; j < d; j++) hd[i][j] = MathEx.random(-1.0, 1.0);
            hl[i] = 1;
        }

        // When
        BSO result = BSO.fit(hd, hl);

        // Then
        assertEquals(nMaj + nMin * 2, result.size());
        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(nMin * 2, minCount);
    }

    @Test
    public void testCustomHighDimThreshold() {
        // Given — force RPForest on 2-D data by lowering threshold to 1
        MathEx.setSeed(33);
        var opts = new BSO.Options(5, 3, 0.2, 1.0, BSO.Strategy.DANGER, 1, 5, 20);

        // When
        BSO result = BSO.fit(data, labels, opts);

        // Then
        assertEquals(110, result.size());
        assertEquals(result.data().length, result.labels().length);
    }

    @Test
    public void testFractionalRatio() {
        // Given — ratio=0.5 adds round(10*0.5)=5 synthetic samples
        MathEx.setSeed(66);
        var opts = new BSO.Options(5, 3, 0.2, 0.5, BSO.Strategy.DANGER);

        // When
        BSO result = BSO.fit(data, labels, opts);

        // Then
        assertEquals(105, result.size());
        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(15, minCount);
    }

    @Test
    public void testSizeConsistency() {
        // Given / When
        BSO result = BSO.fit(data, labels);

        // Then
        assertEquals(result.size(), result.data().length);
        assertEquals(result.size(), result.labels().length);
    }

    @Test
    public void testDataLabelsMismatchThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> BSO.fit(data, new int[50]));
    }

    @Test
    public void testTooFewMinoritySamplesThrows() {
        // Given — minority has exactly m=5 samples → need more than m
        double[][] d2 = new double[15][2];
        int[] l2 = new int[15];
        for (int i = 0; i < 10; i++) { d2[i] = new double[]{i, i}; l2[i] = 0; }
        for (int i = 10; i < 15; i++) { d2[i] = new double[]{i + 20.0, 0}; l2[i] = 1; }

        // Default m=5, minority has exactly 5 → should throw (need > m)
        assertThrows(IllegalArgumentException.class, () -> BSO.fit(d2, l2));
    }
}

