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
package smile.classification;

import org.junit.jupiter.api.Test;
import smile.data.measure.NominalScale;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClassLabels.
 */
class ClassLabelsTest {

    // ── fit(int[]) ─────────────────────────────────────────────────────────────

    @Test
    void givenContiguousLabels_whenFit_thenKAndYAreCorrect() {
        int[] y = {0, 1, 0, 2, 1, 2};
        ClassLabels codec = ClassLabels.fit(y);

        assertEquals(3, codec.k);
        // Labels 0..k-1 are already contiguous; y should be returned as-is.
        assertArrayEquals(y, codec.y);
    }

    @Test
    void givenNonContiguousLabels_whenFit_thenYIsMappedToZeroBasedIndices() {
        // Labels 10, 20, 30 — NOT 0-based
        int[] y = {10, 20, 10, 30, 20, 30};
        ClassLabels codec = ClassLabels.fit(y);

        assertEquals(3, codec.k);

        // y[i] must be in [0, k)
        for (int yi : codec.y) {
            assertTrue(yi >= 0 && yi < codec.k,
                    "Mapped label out of range: " + yi);
        }

        // Original ordering must be preserved through classes.valueOf
        for (int i = 0; i < y.length; i++) {
            assertEquals(y[i], codec.classes.valueOf(codec.y[i]),
                    "Round-trip via classes.valueOf failed at index " + i);
        }
    }

    @Test
    void givenNegativeLabels_whenFit_thenMappedCorrectly() {
        int[] y = {-2, 5, -2, 5, -2};
        ClassLabels codec = ClassLabels.fit(y);

        assertEquals(2, codec.k);
        for (int yi : codec.y) {
            assertTrue(yi == 0 || yi == 1);
        }
    }

    @Test
    void givenSingleClass_whenFit_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ClassLabels.fit(new int[]{1, 1, 1}));
    }

    // ── priori ────────────────────────────────────────────────────────────────

    @Test
    void givenBalancedData_whenFit_thenPrioriAreEqual() {
        int[] y = {0, 1, 0, 1, 0, 1};
        ClassLabels codec = ClassLabels.fit(y);

        assertEquals(0.5, codec.priori[0], 1E-10);
        assertEquals(0.5, codec.priori[1], 1E-10);
    }

    @Test
    void givenImbalancedData_whenFit_thenPrioriSumToOne() {
        int[] y = {0, 0, 0, 1};   // 3:1
        ClassLabels codec = ClassLabels.fit(y);

        double sum = 0;
        for (double p : codec.priori) sum += p;
        assertEquals(1.0, sum, 1E-10);
        assertEquals(0.75, codec.priori[0], 1E-10);
        assertEquals(0.25, codec.priori[1], 1E-10);
    }

    // ── ni (per-class count) ───────────────────────────────────────────────────

    @Test
    void givenData_whenFit_thenNiCountsAreCorrect() {
        int[] y = {0, 0, 1, 2, 2, 2};
        ClassLabels codec = ClassLabels.fit(y);

        assertEquals(2, codec.ni[0]);
        assertEquals(1, codec.ni[1]);
        assertEquals(3, codec.ni[2]);
    }

    // ── indexOf ───────────────────────────────────────────────────────────────

    @Test
    void givenLabels_whenIndexOf_thenReturnsMappedArray() {
        int[] original = {10, 20, 10, 30};
        ClassLabels codec = ClassLabels.fit(original);

        int[] mapped = codec.indexOf(original);
        assertEquals(original.length, mapped.length);
        for (int i = 0; i < original.length; i++) {
            assertEquals(codec.classes.indexOf(original[i]), mapped[i]);
        }
    }

    // ── scale ─────────────────────────────────────────────────────────────────

    @Test
    void givenContiguousLabels_whenScale_thenReturnsNominalScaleWithStringValues() {
        int[] y = {0, 1, 2, 0, 1};
        ClassLabels codec = ClassLabels.fit(y);

        NominalScale scale = codec.scale();
        assertNotNull(scale);
        assertEquals(3, scale.size());
        // Each value should be the string representation of the original label.
        assertEquals("0", scale.toString(0));
        assertEquals("1", scale.toString(1));
        assertEquals("2", scale.toString(2));
    }

    @Test
    void givenNonContiguousLabels_whenScale_thenOriginalLabelsAreRepresented() {
        int[] y = {10, 20, 10};
        ClassLabels codec = ClassLabels.fit(y);

        NominalScale scale = codec.scale();
        assertEquals(2, scale.size());
        // The values must be string representations of the original labels.
        assertEquals("10", scale.toString(0));
        assertEquals("20", scale.toString(1));
    }
}
