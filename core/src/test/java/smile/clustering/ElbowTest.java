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
package smile.clustering;

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Mikita Tsybulka
 */
public class ElbowTest {
    private static final double[][] CENTERS = { { 0, 0 }, { 10, 0 }, { 0, 10 }, { 10, 10 } };
    private static final int expectedK = CENTERS.length;
    private static final int defaultMinK = 2;
    private static final int defaultMaxK = 10;
    private static final int SIZE = 50;

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    /** Generates 4 well-separated Gaussian blobs in 2D. */
    private static double[][] blobs() {
        double[][] data = new double[CENTERS.length * SIZE][];
        for (int c = 0; c < CENTERS.length; c++) {
            for (int i = 0; i < SIZE; i++) {
                data[c * SIZE + i] = new double[] {
                        CENTERS[c][0] + MathEx.randn(),
                        CENTERS[c][1] + MathEx.randn()
                };
            }
        }
        return data;
    }

    @Test
    public void givenSeparatedBlobs_whenFitting_thenSelectNumberOfBlobs() {
        System.out.println("fit");
        // Given 4 well-separated Gaussian blobs
        double[][] data = blobs();

        // When selecting k in [2, 10]
        int k = Elbow.fit(data, defaultMinK, defaultMaxK);

        // Then the number of blobs is found
        assertEquals(expectedK, k);
    }

    @Test
    public void givenDistortionCurve_whenDetectingKnee_thenReturnElbow() {
        System.out.println("knee");
        // Given a distortion curve for k = 1..10
        double[] distortions = { 100, 40, 20, 15, 12, 10, 9, 8, 7.5, 7 };

        // When detecting the knee
        int knee = Elbow.knee(distortions);

        // Then the first elbow is found
        assertEquals(2, knee);
    }

    @Test
    public void givenNonMonotoneCurve_whenDetectingKnee_thenReturnFirstKnee() {
        System.out.println("knee with local optima");
        // Given a distortion curve with a bump from a poor K-Means run
        double[] distortions = { 100, 40, 20, 15, 16, 10, 9, 8, 7.5, 7 };

        // When detecting the knee
        int knee = Elbow.knee(distortions);

        // Then the first elbow is still found
        assertEquals(2, knee);
    }

    @Test
    public void givenTwoElbows_whenDetectingKnee_thenSelectFarthestFromLine() {
        System.out.println("knee with two elbows");
        // Given a curve with a mild first elbow and a strong second one
        double[] distortions = { 100, 90, 81, 79, 40, 30, 28, 27, 26.5, 26 };

        // When detecting the knee
        int knee = Elbow.knee(distortions);

        // Then the point farthest from the line is selected
        assertEquals(5, knee);
    }

    @Test
    public void givenFlatCurve_whenDetectingKnee_thenReturnFirstIndex() {
        System.out.println("knee of constant");
        // Given a constant curve
        double[] distortions = { 5, 5, 5, 5 };

        // When detecting the knee
        int knee = Elbow.knee(distortions);

        // Then the first index is returned
        assertEquals(0, knee);
    }

    @Test
    public void givenFewDistinctPoints_whenFitting_thenThrow() {
        System.out.println("duplicates");
        // Given 100 observations with only 3 distinct values
        double[][] data = new double[100][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new double[] { i % 3 };
        }

        // When maxK exceeds the number of distinct observations
        // Then an exception is thrown instead of degenerate seeding
        assertThrows(IllegalArgumentException.class, () -> Elbow.fit(data, 2, 4));
    }

    @Test
    public void givenNonFiniteValues_whenFittingOrDetectingKnee_thenThrow() {
        System.out.println("non-finite values");
        // Given data with infinite values and a distortion curve with NaN
        double[][] data = { { 0 }, { 1 }, { Double.POSITIVE_INFINITY }, { Double.NEGATIVE_INFINITY }, { 4 } };
        double[] distortions = { 100, Double.NaN, 20, 10 };

        // When fitting or detecting the knee
        // Then an exception is thrown
        assertThrows(IllegalArgumentException.class, () -> Elbow.fit(data, 2, 4));
        assertThrows(IllegalArgumentException.class, () -> Elbow.knee(distortions));
    }

    @Test
    public void givenInvalidInput_whenFitting_thenThrow() {
        System.out.println("invalid input");
        // Given some data
        double[][] data = { { 0, 0 }, { 1, 1 }, { 2, 2 }, { 3, 3 }, { 4, 4 } };

        // When the input is invalid
        // Then an exception is thrown
        assertThrows(IllegalArgumentException.class, () -> Elbow.fit(data, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> Elbow.fit(data, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> Elbow.fit(data, 2, 6));
        assertThrows(IllegalArgumentException.class, () -> Elbow.knee(new double[] { 2, 1 }));
    }
}
