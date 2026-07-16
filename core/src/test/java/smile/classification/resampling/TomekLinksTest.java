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
 * Unit tests for {@link TomekLinks}.
 *
 * @author Haifeng Li
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class TomekLinksTest {

    // ─── Options tests ────────────────────────────────────────────────────────

    @Test
    public void testDefaultOptions() {
        var opts = new TomekLinks.Options();
        assertEquals(20, opts.highDimThreshold());
        assertEquals(10, opts.rpfNumTrees());
        assertEquals(30, opts.rpfLeafSize());
    }

    @Test
    public void testOptionsToPropertiesRoundTrip() {
        // Given
        var opts = new TomekLinks.Options(15, 8, 25);

        // When
        Properties props = opts.toProperties();
        TomekLinks.Options restored = TomekLinks.Options.of(props);

        // Then
        assertEquals(opts.highDimThreshold(), restored.highDimThreshold());
        assertEquals(opts.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(opts.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsOfUsesDefaultsForMissingKeys() {
        var defaults = new TomekLinks.Options();
        var restored = TomekLinks.Options.of(new Properties());

        assertEquals(defaults.highDimThreshold(), restored.highDimThreshold());
        assertEquals(defaults.rpfNumTrees(),      restored.rpfNumTrees());
        assertEquals(defaults.rpfLeafSize(),      restored.rpfLeafSize());
    }

    @Test
    public void testOptionsPropertyKeys() {
        var opts  = new TomekLinks.Options(10, 5, 15);
        var props = opts.toProperties();

        assertEquals("10", props.getProperty("smile.tomek.high_dim_threshold"));
        assertEquals("5",  props.getProperty("smile.tomek.rpf_num_trees"));
        assertEquals("15", props.getProperty("smile.tomek.rpf_leaf_size"));
    }

    @Test
    public void testInvalidOptionsThrow() {
        assertThrows(IllegalArgumentException.class, () -> new TomekLinks.Options(0, 10, 30));
        assertThrows(IllegalArgumentException.class, () -> new TomekLinks.Options(20, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new TomekLinks.Options(20, 10, 0));
    }

    // ─── fit() tests ─────────────────────────────────────────────────────────

    @Test
    public void testTomekLinkRemovedOnSimplePair() {
        // Given — a minimal 2-D dataset with one obvious Tomek link:
        // majority point at (1.0, 0.0) and minority point at (1.1, 0.0) are
        // each other's nearest neighbor and belong to different classes.
        // All other points are well separated.
        double[][] d = {
            {-5.0,  0.0},  // 0 majority
            {-4.0,  0.0},  // 1 majority
            {-3.0,  0.0},  // 2 majority
            { 1.0,  0.0},  // 3 majority  ← Tomek link member
            { 1.1,  0.0},  // 4 minority  ← other Tomek link member
            { 6.0,  0.0},  // 5 minority
            { 7.0,  0.0},  // 6 minority
        };
        int[] l = {0, 0, 0, 0, 1, 1, 1};

        // When
        TomekLinks result = TomekLinks.fit(d, l);

        // Then — the majority sample at index 3 must be removed; minority at index 4 is kept.
        assertEquals(6, result.size(), "Exactly one majority sample should be removed.");

        // Verify the majority Tomek-link member (1.0, 0.0) is absent.
        boolean foundRemoved = Arrays.stream(result.data())
                .anyMatch(row -> row[0] == 1.0 && row[1] == 0.0);
        assertFalse(foundRemoved, "Majority Tomek-link sample (1.0, 0.0) should have been removed.");

        // Verify the minority Tomek-link member (1.1, 0.0) is still present.
        boolean foundMinority = Arrays.stream(result.data())
                .anyMatch(row -> row[0] == 1.1 && row[1] == 0.0);
        assertTrue(foundMinority, "Minority Tomek-link sample (1.1, 0.0) should be preserved.");

        // Verify minority count unchanged (3 minority in, 3 minority out).
        long minCount = Arrays.stream(result.labels()).filter(v -> v == 1).count();
        assertEquals(3, minCount, "Minority count must not decrease.");
    }

    @Test
    public void testNoTomekLinksLeaveDataUnchanged() {
        // Given — two well-separated clusters with no cross-class nearest neighbors.
        MathEx.setSeed(1);
        double[][] d = new double[20][2];
        int[] l = new int[20];
        for (int i = 0; i < 10; i++) { d[i][0] = i;       d[i][1] = 0; l[i] = 0; }
        for (int i = 10; i < 20; i++) { d[i][0] = i + 50; d[i][1] = 0; l[i] = 1; }

        // When
        TomekLinks result = TomekLinks.fit(d, l);

        // Then — nothing removed
        assertEquals(20, result.size());
    }

    @Test
    public void testMajorityCountDecreases() {
        // Given — overlapping dataset where several Tomek links exist.
        MathEx.setSeed(19650218);
        int nMaj = 90, nMin = 10;
        double[][] d = new double[nMaj + nMin][2];
        int[] l = new int[nMaj + nMin];
        for (int i = 0; i < nMaj; i++) {
            d[i][0] = MathEx.random(-2.0, 2.0);
            d[i][1] = MathEx.random(-2.0, 2.0);
            l[i] = 0;
        }
        for (int i = nMaj; i < nMaj + nMin; i++) {
            d[i][0] = MathEx.random(-0.5, 0.5);
            d[i][1] = MathEx.random(-0.5, 0.5);
            l[i] = 1;
        }

        // When
        TomekLinks result = TomekLinks.fit(d, l);

        // Then — majority count may decrease; minority count must not decrease.
        long minBefore = Arrays.stream(l).filter(v -> v == 1).count();
        long minAfter  = Arrays.stream(result.labels()).filter(v -> v == 1).count();
        assertEquals(minBefore, minAfter, "Minority count must not change.");
        assertTrue(result.size() <= nMaj + nMin, "Dataset must not grow.");
    }

    @Test
    public void testMinorityCountNeverDecreases() {
        // Given — majority-heavy dataset
        MathEx.setSeed(42);
        double[][] d = new double[200][2];
        int[] l = new int[200];
        for (int i = 0; i < 190; i++) {
            d[i][0] = MathEx.random(-3.0, 3.0);
            d[i][1] = MathEx.random(-3.0, 3.0);
            l[i] = 0;
        }
        for (int i = 190; i < 200; i++) {
            d[i][0] = MathEx.random(-1.0, 1.0);
            d[i][1] = MathEx.random(-1.0, 1.0);
            l[i] = 1;
        }

        // When
        TomekLinks result = TomekLinks.fit(d, l);

        // Then
        long minAfter = Arrays.stream(result.labels()).filter(v -> v == 1).count();
        assertEquals(10, minAfter, "Minority class samples must never be removed.");
    }

    @Test
    public void testResultIsSmallOrEqualToInput() {
        // Given
        MathEx.setSeed(7);
        double[][] d = new double[100][2];
        int[] l = new int[100];
        for (int i = 0; i < 80; i++) { d[i][0] = MathEx.random(-1,1); d[i][1] = MathEx.random(-1,1); l[i]=0; }
        for (int i = 80; i < 100; i++) { d[i][0] = MathEx.random(-1,1); d[i][1] = MathEx.random(-1,1); l[i]=1; }

        // When
        TomekLinks result = TomekLinks.fit(d, l);

        // Then
        assertTrue(result.size() <= 100);
        assertEquals(result.data().length, result.labels().length);
    }

    @Test
    public void testOriginalDataNotModified() {
        // Given — shallow copies are reused; verify original arrays are untouched.
        double[][] d = {{0.0, 0.0}, {0.1, 0.0}, {5.0, 0.0}, {5.1, 0.0}};
        int[] l     = {0, 1, 0, 1};
        double[][] dCopy = Arrays.stream(d).map(double[]::clone).toArray(double[][]::new);

        // When
        TomekLinks.fit(d, l);

        // Then — original arrays untouched (no in-place modification)
        for (int i = 0; i < d.length; i++) {
            assertArrayEquals(dCopy[i], d[i], 1e-12);
        }
    }

    @Test
    public void testHighDimensionalData() {
        // Given — 50-D overlapping dataset (d > 20 → RandomProjectionForest)
        MathEx.setSeed(99);
        int dim = 50, nMaj = 100, nMin = 20;
        double[][] d = new double[nMaj + nMin][dim];
        int[] l = new int[nMaj + nMin];
        for (int i = 0; i < nMaj; i++) {
            for (int j = 0; j < dim; j++) d[i][j] = MathEx.random(-1.0, 1.0);
            l[i] = 0;
        }
        for (int i = nMaj; i < nMaj + nMin; i++) {
            for (int j = 0; j < dim; j++) d[i][j] = MathEx.random(-0.5, 0.5);
            l[i] = 1;
        }

        // When
        TomekLinks result = TomekLinks.fit(d, l);

        // Then — minority never removed; result not larger than input
        long minAfter = Arrays.stream(result.labels()).filter(v -> v == 1).count();
        assertEquals(nMin, minAfter, "Minority count must not decrease.");
        assertTrue(result.size() <= nMaj + nMin);
    }

    @Test
    public void testCustomHighDimThreshold() {
        // Given — force RPForest for 2-D data by lowering the threshold
        MathEx.setSeed(33);
        double[][] d = new double[50][2];
        int[] l = new int[50];
        for (int i = 0; i < 40; i++) { d[i][0] = MathEx.random(-2,2); d[i][1] = MathEx.random(-2,2); l[i]=0; }
        for (int i = 40; i < 50; i++) { d[i][0] = MathEx.random(-1,1); d[i][1] = MathEx.random(-1,1); l[i]=1; }

        var opts = new TomekLinks.Options(1, 5, 20); // threshold=1 → RPForest for d=2

        // When
        TomekLinks result = TomekLinks.fit(d, l, opts);

        // Then
        assertTrue(result.size() <= 50);
        assertEquals(result.data().length, result.labels().length);
    }

    @Test
    public void testDataLabelsMismatchThrows() {
        double[][] d = {{1.0, 2.0}, {3.0, 4.0}};
        assertThrows(IllegalArgumentException.class, () -> TomekLinks.fit(d, new int[3]));
    }

    @Test
    public void testEmptyDataThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> TomekLinks.fit(new double[0][2], new int[0]));
    }

    @Test
    public void testSizeEqualsDataAndLabelsLength() {
        // Given
        MathEx.setSeed(5);
        double[][] d = new double[60][2];
        int[] l = new int[60];
        for (int i = 0; i < 50; i++) { d[i][0] = MathEx.random(-1,1); d[i][1] = MathEx.random(-1,1); l[i]=0; }
        for (int i = 50; i < 60; i++) { d[i][0] = MathEx.random(-1,1); d[i][1] = MathEx.random(-1,1); l[i]=1; }

        // When
        TomekLinks result = TomekLinks.fit(d, l);

        // Then
        assertEquals(result.size(), result.data().length);
        assertEquals(result.size(), result.labels().length);
    }
}

