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
 * Unit tests for {@link SMOTE}.
 *
 * @author Haifeng Li
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SMOTETest {

    /** A simple 2-D dataset: 90 majority (label=0) and 10 minority (label=1). */
    private static double[][] data;
    private static int[] labels;

    @BeforeAll
    static void setup() {
        MathEx.setSeed(19650218);
        data = new double[100][2];
        labels = new int[100];
        // 90 majority samples around (0, 0)
        for (int i = 0; i < 90; i++) {
            data[i][0] = MathEx.random(-1.0, 1.0);
            data[i][1] = MathEx.random(-1.0, 1.0);
            labels[i] = 0;
        }
        // 10 minority samples around (5, 5)
        for (int i = 90; i < 100; i++) {
            data[i][0] = 5.0 + MathEx.random(-0.5, 0.5);
            data[i][1] = 5.0 + MathEx.random(-0.5, 0.5);
            labels[i] = 1;
        }
    }

    @Test
    public void testOptionsToPropertiesRoundTrip() {
        // Given
        var opts = new SMOTE.Options(7, 1.5, 15, 8, 25);

        // When
        Properties props = opts.toProperties();
        SMOTE.Options restored = SMOTE.Options.of(props);

        // Then
        assertEquals(opts.k(),                restored.k());
        assertEquals(opts.ratio(),            restored.ratio(),            1e-9);
        assertEquals(opts.highDimThreshold(), restored.highDimThreshold());
        assertEquals(opts.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(opts.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsOfUsesDefaultsForMissingKeys() {
        // Given — empty properties should yield the same as new Options()
        var defaults = new SMOTE.Options();

        // When
        SMOTE.Options restored = SMOTE.Options.of(new Properties());

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
        var opts = new SMOTE.Options(3, 2.0, 10, 5, 15);

        // When
        Properties props = opts.toProperties();

        // Then — verify exact property key names
        assertEquals("3",   props.getProperty("smile.smote.k"));
        assertEquals("2.0", props.getProperty("smile.smote.ratio"));
        assertEquals("10",  props.getProperty("smile.smote.high_dim_threshold"));
        assertEquals("5",   props.getProperty("smile.smote.rpf_num_trees"));
        assertEquals("15",  props.getProperty("smile.smote.rpf_leaf_size"));
    }

    @Test
    public void testDefaultOptions() {
        // Given / When / Then
        var opts = new SMOTE.Options();
        assertEquals(5,    opts.k());
        assertEquals(1.0,  opts.ratio(), 1e-9);
        assertEquals(20,   opts.highDimThreshold());
        assertEquals(10,   opts.rpfNumTrees());
        assertEquals(30,   opts.rpfLeafSize());
    }

    @Test
    public void testFitDoublesMinorityClass() {
        // Given
        var opts = new SMOTE.Options(5, 1.0, 20, 10, 30);

        // When — 100 original + 10 synthetic (100% of 10)
        SMOTE result = SMOTE.fit(data, labels, opts);

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
    public void testFitDefaultOptions() {
        // Given / When
        SMOTE result = SMOTE.fit(data, labels);

        // Then — same as ratio=1.0
        assertEquals(110, result.size());
    }

    @Test
    public void testFitWithRatioTwo() {
        // Given — ratio=2.0 should triple the minority class (10 + 20 synthetic)
        var opts = new SMOTE.Options(5, 2.0, 20, 10, 30);

        // When
        SMOTE result = SMOTE.fit(data, labels, opts);

        // Then
        assertEquals(120, result.size());
        long minorityCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(30, minorityCount);
    }

    @Test
    public void testSyntheticSamplesAreBetweenNeighbors() {
        // Given — use k=3 on the minority cluster at (5,5)
        MathEx.setSeed(12345);
        var opts = new SMOTE.Options(3, 1.0, 20, 10, 30);
        SMOTE result = SMOTE.fit(data, labels, opts);

        // When — extract the 10 synthetic minority samples
        double[][] synthetic = new double[10][];
        int idx = 0;
        for (int i = 100; i < 110; i++) {
            synthetic[idx++] = result.data()[i];
        }

        // Then — every synthetic sample should be within the minority cluster hull
        for (double[] s : synthetic) {
            assertTrue(s[0] >= 4.0 && s[0] <= 6.0,
                    "x coordinate out of expected range: " + s[0]);
            assertTrue(s[1] >= 4.0 && s[1] <= 6.0,
                    "y coordinate out of expected range: " + s[1]);
        }
    }

    @Test
    public void testOriginalSamplesPreserved() {
        // Given / When
        SMOTE result = SMOTE.fit(data, labels);

        // Then — first 100 rows must be identical to the original data
        for (int i = 0; i < 100; i++) {
            assertArrayEquals(data[i], result.data()[i], 1e-12, "Row " + i + " was modified.");
            assertEquals(labels[i], result.labels()[i], "Label " + i + " was modified.");
        }
    }

    @Test
    public void testMinorityLabelIsCorrectlyDetected() {
        // Given — label=2 is the minority (2 samples)
        double[][] d = new double[15][2];
        int[] l = new int[15];
        for (int i = 0; i < 10; i++) { d[i] = new double[]{i, i};          l[i] = 0; }
        for (int i = 10; i < 13; i++) { d[i] = new double[]{i, i};         l[i] = 1; }
        for (int i = 13; i < 15; i++) { d[i] = new double[]{i * 10.0, i * 10.0}; l[i] = 2; }

        var opts = new SMOTE.Options(1, 1.0, 20, 10, 30);

        // When
        SMOTE result = SMOTE.fit(d, l, opts);

        // Then — 15 original + 2 synthetic = 17
        assertEquals(17, result.size());
        long count2 = Arrays.stream(result.labels()).filter(v -> v == 2).count();
        assertEquals(4, count2);
    }

    @Test
    public void testHighDimensionalDataUsesApproximateNN() {
        // Given — 50-dimensional data (d > 20 triggers RandomProjectionForest)
        MathEx.setSeed(42);
        int d = 50, nMinority = 40, nMajority = 200;
        double[][] hd = new double[nMajority + nMinority][d];
        int[] hl = new int[nMajority + nMinority];
        for (int i = 0; i < nMajority; i++) {
            for (int j = 0; j < d; j++) hd[i][j] = MathEx.random(-1.0, 1.0);
            hl[i] = 0;
        }
        for (int i = nMajority; i < nMajority + nMinority; i++) {
            for (int j = 0; j < d; j++) hd[i][j] = 10.0 + MathEx.random(-0.5, 0.5);
            hl[i] = 1;
        }

        // When
        SMOTE result = SMOTE.fit(hd, hl);

        // Then
        assertEquals(nMajority + nMinority * 2, result.size());
        long minorityCount = Arrays.stream(result.labels()).filter(lv -> lv == 1).count();
        assertEquals(nMinority * 2, minorityCount);

        // Synthetic samples should lie within [9.0, 11.0] in every dimension
        for (int i = nMajority + nMinority; i < result.size(); i++) {
            double[] s = result.data()[i];
            for (int j = 0; j < d; j++) {
                assertTrue(s[j] >= 9.0 && s[j] <= 11.0,
                        "dim " + j + " out of expected range: " + s[j]);
            }
        }
    }

    @Test
    public void testCustomHighDimThreshold() {
        // Given — lower the threshold to 1 so RPForest is used even for 2-D data
        MathEx.setSeed(99);
        var opts = new SMOTE.Options(5, 1.0, 1, 5, 20);

        // When
        SMOTE result = SMOTE.fit(data, labels, opts);

        // Then — behaviour should still be correct regardless of index type
        assertEquals(110, result.size());
        long minorityCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(20, minorityCount);
    }

    @Test
    public void testFractionalRatio() {
        // Given — ratio=0.5 adds round(10*0.5)=5 synthetic samples
        var opts = new SMOTE.Options(5, 0.5, 20, 10, 30);

        // When
        SMOTE result = SMOTE.fit(data, labels, opts);

        // Then
        assertEquals(105, result.size());
        long minorityCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(15, minorityCount);
    }

    @Test
    public void testInvalidOptionsThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SMOTE.Options(0, 1.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SMOTE.Options(5, 0.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SMOTE.Options(5, -1.0, 20, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SMOTE.Options(5, 1.0, 0, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new SMOTE.Options(5, 1.0, 20, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new SMOTE.Options(5, 1.0, 20, 10, 0));
    }

    @Test
    public void testDataLabelsMismatchThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SMOTE.fit(data, new int[50]));
    }

    @Test
    public void testTooFewMinoritySamplesThrows() {
        // Given — minority has 2 samples but k=5
        double[][] d = new double[12][2];
        int[] l = new int[12];
        for (int i = 0; i < 10; i++) { d[i] = new double[]{i, i}; l[i] = 0; }
        d[10] = new double[]{20, 20}; l[10] = 1;
        d[11] = new double[]{21, 21}; l[11] = 1;

        assertThrows(IllegalArgumentException.class,
                () -> SMOTE.fit(d, l)); // default k=5 > 2 minority samples
    }
}
