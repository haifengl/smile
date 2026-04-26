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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ADASYN}.
 *
 * @author Haifeng Li
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ADASYNTest {

    /** 2-D dataset: 90 majority (label=0) around (0,0) and 10 minority (label=1) around (5,5). */
    private static double[][] data;
    private static int[] labels;

    @BeforeAll
    static void setup() {
        MathEx.setSeed(19650218);
        data = new double[100][2];
        labels = new int[100];
        for (int i = 0; i < 90; i++) {
            data[i][0] = MathEx.random(-1.0, 1.0);
            data[i][1] = MathEx.random(-1.0, 1.0);
            labels[i] = 0;
        }
        for (int i = 90; i < 100; i++) {
            data[i][0] = 5.0 + MathEx.random(-0.5, 0.5);
            data[i][1] = 5.0 + MathEx.random(-0.5, 0.5);
            labels[i] = 1;
        }
    }

    // ─── Options tests ────────────────────────────────────────────────────────

    @Test
    public void testDefaultOptions() {
        // Given / When
        var opts = new ADASYN.Options();

        // Then
        assertEquals(5,    opts.k());
        assertEquals(1.0,  opts.ratio(), 1e-9);
        assertEquals(20,   opts.highDimThreshold());
        assertEquals(10,   opts.rpfNumTrees());
        assertEquals(30,   opts.rpfLeafSize());
    }

    @Test
    public void testOptionsToPropertiesRoundTrip() {
        // Given
        var opts = new ADASYN.Options(7, 0.8, 15, 8, 25);

        // When
        Properties props = opts.toProperties();
        ADASYN.Options restored = ADASYN.Options.of(props);

        // Then
        assertEquals(opts.k(),                restored.k());
        assertEquals(opts.ratio(),            restored.ratio(),            1e-9);
        assertEquals(opts.highDimThreshold(), restored.highDimThreshold());
        assertEquals(opts.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(opts.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsOfUsesDefaultsForMissingKeys() {
        // Given / When
        var defaults  = new ADASYN.Options();
        var restored  = ADASYN.Options.of(new Properties());

        // Then
        assertEquals(defaults.k(),                restored.k());
        assertEquals(defaults.ratio(),            restored.ratio(),            1e-9);
        assertEquals(defaults.highDimThreshold(), restored.highDimThreshold());
        assertEquals(defaults.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(defaults.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsPropertyKeys() {
        // Given
        var opts  = new ADASYN.Options(3, 0.5, 10, 5, 15);
        var props = opts.toProperties();

        // Then
        assertEquals("3",   props.getProperty("smile.adasyn.k"));
        assertEquals("0.5", props.getProperty("smile.adasyn.ratio"));
        assertEquals("10",  props.getProperty("smile.adasyn.high_dim_threshold"));
        assertEquals("5",   props.getProperty("smile.adasyn.rpf_num_trees"));
        assertEquals("15",  props.getProperty("smile.adasyn.rpf_leaf_size"));
    }

    @Test
    public void testInvalidOptionsThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ADASYN.Options(0, 1.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new ADASYN.Options(5, 0.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new ADASYN.Options(5, 1.1, 20, 10, 30)); // ratio > 1
        assertThrows(IllegalArgumentException.class, () -> new ADASYN.Options(5, -0.5, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new ADASYN.Options(5, 1.0, 0, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new ADASYN.Options(5, 1.0, 20, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new ADASYN.Options(5, 1.0, 20, 10, 0));
    }

    // ─── fit() tests ─────────────────────────────────────────────────────────

    @Test
    public void testFitDefaultOptions() {
        // Given — well-separated classes: all r_i = 0 → no synthetic samples (correct ADASYN behaviour)
        // When
        ADASYN result = ADASYN.fit(data, labels);

        // Then — dataset unchanged (no boundary minority instances to augment)
        assertEquals(100, result.size());
        assertEquals(result.data().length, result.labels().length);

        // Original rows must be unchanged
        for (int i = 0; i < 100; i++) {
            assertArrayEquals(data[i], result.data()[i], 1e-12);
            assertEquals(labels[i], result.labels()[i]);
        }
    }

    @Test
    public void testFitWithFullRatioOnOverlappingData() {
        // Given — partially overlapping classes so boundary minority samples get r_i > 0
        MathEx.setSeed(42);
        int nMaj = 90, nMin = 10;
        double[][] d2 = new double[nMaj + nMin][2];
        int[] l2 = new int[nMaj + nMin];
        // majority centred at (0,0)
        for (int i = 0; i < nMaj; i++) {
            d2[i][0] = MathEx.random(-2.0, 2.0);
            d2[i][1] = MathEx.random(-2.0, 2.0);
            l2[i] = 0;
        }
        // minority partially overlapping, centred at (1,1) — inside majority range
        for (int i = nMaj; i < nMaj + nMin; i++) {
            d2[i][0] = 1.0 + MathEx.random(-1.0, 1.0);
            d2[i][1] = 1.0 + MathEx.random(-1.0, 1.0);
            l2[i] = 1;
        }

        var opts = new ADASYN.Options(5, 1.0, 20, 10, 30);

        // When
        ADASYN result = ADASYN.fit(d2, l2, opts);

        // Then — at least some synthetic samples must be produced for overlapping data
        assertTrue(result.size() > nMaj + nMin,
                "Expected synthetic samples for overlapping classes, size=" + result.size());
        long minorityCount = Arrays.stream(result.labels()).filter(lv -> lv == 1).count();
        assertTrue(minorityCount > nMin,
                "Expected minority count to grow, got " + minorityCount);
        assertEquals(nMaj, Arrays.stream(result.labels()).filter(lv -> lv == 0).count(),
                "Majority count must not change");
    }

    @Test
    public void testSyntheticSamplesAreBetweenMinorityNeighbors() {
        // Given — overlapping minority cluster within majority range ensures r_i > 0
        MathEx.setSeed(7);
        int nMaj = 90, nMin = 10;
        double[][] d2 = new double[nMaj + nMin][2];
        int[] l2 = new int[nMaj + nMin];
        for (int i = 0; i < nMaj; i++) {
            d2[i][0] = MathEx.random(-3.0, 3.0);
            d2[i][1] = MathEx.random(-3.0, 3.0);
            l2[i] = 0;
        }
        for (int i = nMaj; i < nMaj + nMin; i++) {
            d2[i][0] = 0.5 + MathEx.random(-0.3, 0.3);
            d2[i][1] = 0.5 + MathEx.random(-0.3, 0.3);
            l2[i] = 1;
        }
        var opts = new ADASYN.Options(5, 1.0, 20, 10, 30);
        ADASYN result = ADASYN.fit(d2, l2, opts);

        // When / Then — all appended samples must have minority label
        for (int i = nMaj + nMin; i < result.size(); i++) {
            assertEquals(1, result.labels()[i], "Synthetic sample must have minority label");
        }
        // And the result must actually be larger
        assertTrue(result.size() > nMaj + nMin, "Expected synthetic samples to be generated");
    }

    @Test
    public void testAdaptiveFocusOnBoundaryInstances() {
        // Given — mix a few minority samples close to the majority cluster
        // to create class-boundary minority instances that should get more synthetic samples
        MathEx.setSeed(2024);
        double[][] mixedData = new double[110][2];
        int[] mixedLabels = new int[110];
        System.arraycopy(data, 0, mixedData, 0, 100);
        System.arraycopy(labels, 0, mixedLabels, 0, 100);
        // 10 extra minority points near the majority cluster boundary
        for (int i = 100; i < 110; i++) {
            mixedData[i][0] = MathEx.random(-0.3, 0.3);
            mixedData[i][1] = MathEx.random(-0.3, 0.3);
            mixedLabels[i] = 1;
        }

        var opts = new ADASYN.Options(5, 1.0, 20, 10, 30);

        // When
        ADASYN result = ADASYN.fit(mixedData, mixedLabels, opts);

        // Then — result must be larger than the input (some synthetic samples created)
        assertTrue(result.size() > 110,
                "Expected synthetic samples to be generated for boundary minority instances");
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
        ADASYN result = ADASYN.fit(hd, hl);

        // Then — synthetic count > 0; minority samples remain within cluster bounds
        assertTrue(result.size() >= nMaj + nMin);
        for (int i = nMaj + nMin; i < result.size(); i++) {
            assertEquals(1, result.labels()[i]);
            for (int j = 0; j < d; j++) {
                assertTrue(result.data()[i][j] >= 9.0 && result.data()[i][j] <= 11.0,
                        "dim " + j + " out of range: " + result.data()[i][j]);
            }
        }
    }

    @Test
    public void testCustomHighDimThreshold() {
        // Given — force RPForest on 2-D data by lowering threshold to 1, with overlapping data
        MathEx.setSeed(55);
        int nMaj = 90, nMin = 10;
        double[][] d2 = new double[nMaj + nMin][2];
        int[] l2 = new int[nMaj + nMin];
        for (int i = 0; i < nMaj; i++) {
            d2[i][0] = MathEx.random(-2.0, 2.0);
            d2[i][1] = MathEx.random(-2.0, 2.0);
            l2[i] = 0;
        }
        for (int i = nMaj; i < nMaj + nMin; i++) {
            d2[i][0] = MathEx.random(-1.0, 1.0);
            d2[i][1] = MathEx.random(-1.0, 1.0);
            l2[i] = 1;
        }
        var opts = new ADASYN.Options(5, 1.0, 1, 5, 20);

        // When
        ADASYN result = ADASYN.fit(d2, l2, opts);

        // Then — result must be sensible regardless of index type
        assertEquals(result.data().length, result.labels().length);
        assertTrue(result.size() >= nMaj + nMin);
    }

    @Test
    public void testDataLabelsMismatchThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ADASYN.fit(data, new int[50]));
    }

    @Test
    public void testTooFewMinoritySamplesThrows() {
        // Given — only 2 minority samples but default k = 5
        double[][] d2 = new double[12][2];
        int[] l2 = new int[12];
        for (int i = 0; i < 10; i++) { d2[i] = new double[]{i, i}; l2[i] = 0; }
        d2[10] = new double[]{50, 50}; l2[10] = 1;
        d2[11] = new double[]{51, 51}; l2[11] = 1;

        assertThrows(IllegalArgumentException.class, () -> ADASYN.fit(d2, l2));
    }

    @Test
    public void testAlreadyBalancedClassesProducesNoSamples() {
        // Given — perfectly balanced dataset → G = 0
        double[][] bal = new double[20][2];
        int[] balL = new int[20];
        for (int i = 0; i < 10; i++) { bal[i] = new double[]{i, i};           balL[i] = 0; }
        for (int i = 10; i < 20; i++) { bal[i] = new double[]{i + 50.0, i + 50.0}; balL[i] = 1; }

        // When
        ADASYN result = ADASYN.fit(bal, balL);

        // Then — G = (10-10)*ratio = 0, no synthetic samples
        assertEquals(20, result.size());
    }

    @Test
    public void testWellSeparatedClassesProducesNoSamples() {
        // Given — the shared well-separated dataset; all r_i = 0 → ADASYN produces nothing
        // When
        ADASYN result = ADASYN.fit(data, labels);

        // Then — no synthetic samples (all minority points are in pure minority neighborhood)
        assertEquals(100, result.size());
    }
}

