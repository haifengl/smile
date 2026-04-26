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
        // 90 majority samples around (0, 0)
        // 10 minority samples around (5, 5)
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

    @Test
    public void testDefaultConstructor() {
        // Given
        SMOTE smote = new SMOTE();

        // When / Then
        assertEquals(5, smote.k());
        assertEquals(1.0, smote.ratio(), 1e-9);
    }

    @Test
    public void testResampleDoublesMinorityClass() {
        // Given
        SMOTE smote = new SMOTE(5, 1.0);

        // When
        SMOTE.Result result = smote.resample(data, labels);

        // Then — 100 original + 10 synthetic (100% of 10)
        assertEquals(110, result.size());
        assertEquals(110, result.data().length);
        assertEquals(110, result.labels().length);

        long minorityCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(20, minorityCount);

        long majorityCount = Arrays.stream(result.labels()).filter(l -> l == 0).count();
        assertEquals(90, majorityCount);
    }

    @Test
    public void testResampleWithRatioTwo() {
        // Given — ratio=2.0 should triple the minority class (10 + 20 synthetic)
        SMOTE smote = new SMOTE(5, 2.0);

        // When
        SMOTE.Result result = smote.resample(data, labels);

        // Then
        assertEquals(120, result.size());
        long minorityCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(30, minorityCount);
    }

    @Test
    public void testSyntheticSamplesAreBetweenNeighbors() {
        // Given — use k=3, ratio=1.0 on the minority cluster at (5,5)
        MathEx.setSeed(12345);
        SMOTE smote = new SMOTE(3, 1.0);
        SMOTE.Result result = smote.resample(data, labels);

        // When — extract the 10 synthetic minority samples
        double[][] synthetic = new double[10][];
        int idx = 0;
        for (int i = 100; i < 110; i++) {
            synthetic[idx++] = result.data()[i];
        }

        // Then — every synthetic sample should be within the minority cluster hull
        // (strictly between ~4.5 and ~5.5 in both dimensions)
        for (double[] s : synthetic) {
            assertTrue(s[0] >= 4.0 && s[0] <= 6.0,
                    "x coordinate out of expected range: " + s[0]);
            assertTrue(s[1] >= 4.0 && s[1] <= 6.0,
                    "y coordinate out of expected range: " + s[1]);
        }
    }

    @Test
    public void testOriginalSamplesPreserved() {
        // Given
        SMOTE smote = new SMOTE(5, 1.0);

        // When
        SMOTE.Result result = smote.resample(data, labels);

        // Then — first 100 rows must be identical to the original data
        for (int i = 0; i < 100; i++) {
            assertArrayEquals(data[i], result.data()[i], 1e-12, "Row " + i + " was modified.");
            assertEquals(labels[i], result.labels()[i], "Label " + i + " was modified.");
        }
    }

    @Test
    public void testMinorityLabelIsCorrectlyDetected() {
        // Given — create dataset where label=2 is the minority
        double[][] d = new double[15][2];
        int[] l = new int[15];
        for (int i = 0; i < 10; i++) { d[i] = new double[]{i, i};     l[i] = 0; }
        for (int i = 10; i < 13; i++) { d[i] = new double[]{i, i};    l[i] = 1; }
        for (int i = 13; i < 15; i++) { d[i] = new double[]{i * 10, i * 10}; l[i] = 2; }

        SMOTE smote = new SMOTE(1, 1.0);

        // When
        SMOTE.Result result = smote.resample(d, l);

        // Then — the two label-2 samples should be doubled; total = 15 + 2 = 17
        assertEquals(17, result.size());
        long count2 = Arrays.stream(result.labels()).filter(v -> v == 2).count();
        assertEquals(4, count2);
    }

    @Test
    public void testInvalidKThrows() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> new SMOTE(0, 1.0));
    }

    @Test
    public void testInvalidRatioThrows() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> new SMOTE(5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new SMOTE(5, -1.0));
    }

    @Test
    public void testDataLabelsMismatchThrows() {
        // Given
        SMOTE smote = new SMOTE();

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> smote.resample(data, new int[50]));
    }

    @Test
    public void testTooFewMinoritySamplesThrows() {
        // Given — minority has 2 samples but k=5
        double[][] d = new double[12][2];
        int[] l = new int[12];
        for (int i = 0; i < 10; i++) { d[i] = new double[]{i, i}; l[i] = 0; }
        d[10] = new double[]{20, 20}; l[10] = 1;
        d[11] = new double[]{21, 21}; l[11] = 1;

        SMOTE smote = new SMOTE(5, 1.0);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> smote.resample(d, l));
    }

    @Test
    public void testFractionalRatio() {
        // Given — ratio=0.5 should add floor(10*0.5)=5 synthetic samples
        SMOTE smote = new SMOTE(5, 0.5);

        // When
        SMOTE.Result result = smote.resample(data, labels);

        // Then
        assertEquals(105, result.size());
        long minorityCount = Arrays.stream(result.labels()).filter(l -> l == 1).count();
        assertEquals(15, minorityCount);
    }
}

