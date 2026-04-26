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
package smile.classification.imbalance;

import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.*;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.LinearKernel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SVMSMOTE}.
 *
 * @author Haifeng Li
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SVMSMOTETest {

    /** 2-D dataset: 90 majority (label=0) and 10 minority (label=1). */
    private static double[][] data;
    private static int[] labels;

    @BeforeAll
    static void setup() {
        MathEx.setSeed(19650218);
        data = new double[100][2];
        labels = new int[100];
        // majority around (0, 0)
        for (int i = 0; i < 90; i++) {
            data[i][0] = MathEx.random(-1.0, 1.0);
            data[i][1] = MathEx.random(-1.0, 1.0);
            labels[i] = 0;
        }
        // minority around (5, 5) – clearly separated so SVM finds clean boundary
        for (int i = 90; i < 100; i++) {
            data[i][0] = 5.0 + MathEx.random(-0.5, 0.5);
            data[i][1] = 5.0 + MathEx.random(-0.5, 0.5);
            labels[i] = 1;
        }
    }

    // ─── Options tests ────────────────────────────────────────────────────────

    @Test
    public void testDefaultOptions() {
        var opts = new SVMSMOTE.Options();
        assertEquals(5,    opts.k());
        assertEquals(1.0,  opts.ratio(),   1e-9);
        assertEquals(10.0, opts.mFactor(), 1e-9);
        assertEquals(1.0,  opts.svmC(),    1e-9);
        assertEquals(20,   opts.highDimThreshold());
        assertEquals(10,   opts.rpfNumTrees());
        assertEquals(30,   opts.rpfLeafSize());
    }

    @Test
    public void testOptionsToPropertiesRoundTrip() {
        // Given
        var opts = new SVMSMOTE.Options(7, 1.5, 5.0, 2.0, 15, 8, 25);

        // When
        Properties props = opts.toProperties();
        SVMSMOTE.Options restored = SVMSMOTE.Options.of(props);

        // Then
        assertEquals(opts.k(),                restored.k());
        assertEquals(opts.ratio(),            restored.ratio(),   1e-9);
        assertEquals(opts.mFactor(),          restored.mFactor(), 1e-9);
        assertEquals(opts.svmC(),             restored.svmC(),    1e-9);
        assertEquals(opts.highDimThreshold(), restored.highDimThreshold());
        assertEquals(opts.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(opts.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsOfUsesDefaultsForMissingKeys() {
        var defaults = new SVMSMOTE.Options();
        var restored = SVMSMOTE.Options.of(new Properties());

        assertEquals(defaults.k(),                restored.k());
        assertEquals(defaults.ratio(),            restored.ratio(),   1e-9);
        assertEquals(defaults.mFactor(),          restored.mFactor(), 1e-9);
        assertEquals(defaults.svmC(),             restored.svmC(),    1e-9);
        assertEquals(defaults.highDimThreshold(), restored.highDimThreshold());
        assertEquals(defaults.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(defaults.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsPropertyKeys() {
        var opts  = new SVMSMOTE.Options(3, 2.0, 8.0, 0.5, 10, 5, 15);
        var props = opts.toProperties();

        assertEquals("3",   props.getProperty("smile.svmsmote.k"));
        assertEquals("2.0", props.getProperty("smile.svmsmote.ratio"));
        assertEquals("8.0", props.getProperty("smile.svmsmote.m_factor"));
        assertEquals("0.5", props.getProperty("smile.svmsmote.svm_c"));
        assertEquals("10",  props.getProperty("smile.svmsmote.high_dim_threshold"));
        assertEquals("5",   props.getProperty("smile.svmsmote.rpf_num_trees"));
        assertEquals("15",  props.getProperty("smile.svmsmote.rpf_leaf_size"));
    }

    @Test
    public void testInvalidOptionsThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SVMSMOTE.Options(0, 1.0, 10, 1.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SVMSMOTE.Options(5, 0.0, 10, 1.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SVMSMOTE.Options(5, -1.0, 10, 1.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SVMSMOTE.Options(5, 1.0, -1.0, 1.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SVMSMOTE.Options(5, 1.0, 10, 0.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SVMSMOTE.Options(5, 1.0, 10, 1.0, 0, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SVMSMOTE.Options(5, 1.0, 10, 1.0, 20, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new SVMSMOTE.Options(5, 1.0, 10, 1.0, 20, 10, 0));
    }

    // ─── fit() tests ─────────────────────────────────────────────────────────

    @Test
    public void testFitDefaultOptionsDoublesMinorityClass() {
        // Given / When — default ratio=1.0 should add 10 synthetic samples
        MathEx.setSeed(42);
        SVMSMOTE result = SVMSMOTE.fit(data, labels);

        // Then
        assertEquals(110, result.size());
        assertEquals(110, result.data().length);
        assertEquals(110, result.labels().length);

        long minorityCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(20, minorityCount);
        long majorityCount = Arrays.stream(result.labels()).filter(l -> l == 0).count();
        assertEquals(90, majorityCount);
    }

    @Test
    public void testFitWithExplicitOptions() {
        // Given — ratio=2.0 triples minority (10 + 20 = 30)
        MathEx.setSeed(7);
        var opts = new SVMSMOTE.Options(5, 2.0);

        // When
        SVMSMOTE result = SVMSMOTE.fit(data, labels, opts);

        // Then
        assertEquals(120, result.size());
        long minorityCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(30, minorityCount);
    }

    @Test
    public void testFitWithLinearKernel() {
        // Given — use a linear kernel explicitly
        MathEx.setSeed(11);
        var opts = new SVMSMOTE.Options(5, 1.0);

        // When
        SVMSMOTE result = SVMSMOTE.fit(data, labels, opts, new LinearKernel());

        // Then
        assertEquals(110, result.size());
        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(20, minCount);
    }

    @Test
    public void testOriginalSamplesPreserved() {
        // Given / When
        MathEx.setSeed(3);
        SVMSMOTE result = SVMSMOTE.fit(data, labels);

        // Then — first 100 rows unchanged
        for (int i = 0; i < 100; i++) {
            assertArrayEquals(data[i], result.data()[i], 1e-12, "Row " + i + " was modified.");
            assertEquals(labels[i], result.labels()[i], "Label " + i + " was modified.");
        }
    }

    @Test
    public void testSyntheticSamplesHaveMinorityLabel() {
        // Given / When
        MathEx.setSeed(13);
        SVMSMOTE result = SVMSMOTE.fit(data, labels);

        // Then — all appended rows have minority label
        for (int i = 100; i < result.size(); i++) {
            assertEquals(1, result.labels()[i],
                    "Synthetic sample at row " + i + " has wrong label.");
        }
    }

    @Test
    public void testSyntheticSamplesAreWithinMinorityRegion() {
        // Given — well-separated minority at (5,5); synthetics must stay near cluster
        MathEx.setSeed(21);
        SVMSMOTE result = SVMSMOTE.fit(data, labels);

        for (int i = 100; i < result.size(); i++) {
            double[] s = result.data()[i];
            // Minority cluster spans [4.5, 5.5]; allow a small margin for extrapolation
            assertTrue(s[0] >= 3.5 && s[0] <= 6.5,
                    "x coordinate out of expected range: " + s[0]);
            assertTrue(s[1] >= 3.5 && s[1] <= 6.5,
                    "y coordinate out of expected range: " + s[1]);
        }
    }

    @Test
    public void testFallbackToAllMinorityWhenNoSupportVectors() {
        // Given — very large mFactor forces fallback (threshold >> scores)
        MathEx.setSeed(55);
        var opts = new SVMSMOTE.Options(5, 1.0, 1e6, 1.0, 20, 10, 30);

        // When
        SVMSMOTE result = SVMSMOTE.fit(data, labels, opts);

        // Then — result is still correct even when all minority are used as seeds
        assertEquals(110, result.size());
        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(20, minCount);
    }

    @Test
    public void testHighDimensionalData() {
        // Given — 50-D dataset (d > 20 → RandomProjectionForest)
        MathEx.setSeed(99);
        int d = 50, nMin = 40, nMaj = 200;
        double[][] hd = new double[nMaj + nMin][d];
        int[] hl = new int[nMaj + nMin];
        for (int i = 0; i < nMaj; i++) {
            for (int j = 0; j < d; j++) hd[i][j] = MathEx.random(-1.0, 1.0);
            hl[i] = 0;
        }
        for (int i = nMaj; i < nMaj + nMin; i++) {
            for (int j = 0; j < d; j++) hd[i][j] = 10.0 + MathEx.random(-0.5, 0.5);
            hl[i] = 1;
        }

        // When
        SVMSMOTE result = SVMSMOTE.fit(hd, hl);

        // Then
        assertEquals(nMaj + nMin * 2, result.size());
        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(nMin * 2, minCount);

        // Synthetic samples should be within [9.0, 11.0] in every dimension
        for (int i = nMaj + nMin; i < result.size(); i++) {
            for (int j = 0; j < d; j++) {
                assertTrue(result.data()[i][j] >= 9.0 && result.data()[i][j] <= 11.0,
                        "dim " + j + " out of range: " + result.data()[i][j]);
            }
        }
    }

    @Test
    public void testFractionalRatio() {
        // Given — ratio=0.5 adds round(10*0.5)=5 synthetic samples
        MathEx.setSeed(77);
        var opts = new SVMSMOTE.Options(5, 0.5);

        // When
        SVMSMOTE result = SVMSMOTE.fit(data, labels, opts);

        // Then
        assertEquals(105, result.size());
        long minCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(15, minCount);
    }

    @Test
    public void testSizeEqualsDataAndLabelsLength() {
        // Given / When
        SVMSMOTE result = SVMSMOTE.fit(data, labels);

        // Then
        assertEquals(result.size(), result.data().length);
        assertEquals(result.size(), result.labels().length);
    }

    @Test
    public void testDataLabelsMismatchThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SVMSMOTE.fit(data, new int[50]));
    }

    @Test
    public void testTooFewMinoritySamplesThrows() {
        double[][] d2 = new double[12][2];
        int[] l2 = new int[12];
        for (int i = 0; i < 10; i++) { d2[i] = new double[]{i, i}; l2[i] = 0; }
        d2[10] = new double[]{50, 50}; l2[10] = 1;
        d2[11] = new double[]{51, 51}; l2[11] = 1;

        assertThrows(IllegalArgumentException.class, () -> SVMSMOTE.fit(d2, l2));
    }
}

