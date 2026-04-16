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
package smile.nlp.relevance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BM25}.
 *
 * @author Haifeng Li
 */
public class BM25Test {

    // -----------------------------------------------------------------------
    // Constructor validation & accessors
    // -----------------------------------------------------------------------

    @Test
    public void testDefaultParameters() {
        // Given: default constructor
        BM25 bm25 = new BM25();
        // When / Then
        assertEquals(1.2,  bm25.k1(),    1e-9);
        assertEquals(0.75, bm25.b(),     1e-9);
        assertEquals(1.0,  bm25.delta(), 1e-9);
    }

    @Test
    public void testCustomParameters() {
        // Given / When
        BM25 bm25 = new BM25(2.0, 0.5, 0.0);
        // Then
        assertEquals(2.0, bm25.k1(),    1e-9);
        assertEquals(0.5, bm25.b(),     1e-9);
        assertEquals(0.0, bm25.delta(), 1e-9);
    }

    @Test
    public void testNegativeK1Throws() {
        // Given: k1 < 0
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new BM25(-0.1, 0.75, 0.0));
    }

    @Test
    public void testBelowZeroBThrows() {
        // Given: b < 0
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new BM25(1.2, -0.1, 0.0));
    }

    @Test
    public void testAboveOneBThrows() {
        // Given: b > 1
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new BM25(1.2, 1.1, 0.0));
    }

    @Test
    public void testNegativeDeltaThrows() {
        // Given: delta < 0
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> new BM25(1.2, 0.75, -0.1));
    }

    @Test
    public void testBoundaryK1Zero() {
        // Given: k1 = 0 (binary model)
        // When / Then: should not throw
        assertDoesNotThrow(() -> new BM25(0.0, 0.75, 0.0));
    }

    @Test
    public void testBoundaryBZero() {
        // Given: b = 0 (no length normalisation — BM15)
        // When / Then: should not throw
        assertDoesNotThrow(() -> new BM25(1.2, 0.0, 0.0));
    }

    @Test
    public void testBoundaryBOne() {
        // Given: b = 1 (full length normalisation — BM11)
        // When / Then: should not throw
        assertDoesNotThrow(() -> new BM25(1.2, 1.0, 0.0));
    }

    // -----------------------------------------------------------------------
    // score(double freq, int docSize, double avgDocSize, long N, long n)
    // -----------------------------------------------------------------------

    @Test
    public void testScoreWithDocLengthBaseline() {
        // Given
        BM25 instance = new BM25(2.0, 0.75, 0.0);
        // When
        double result = instance.score(3, 100, 150, 10_000_000, 1_000);
        // Then: hand-computed expected value
        assertEquals(18.419681, result, 1e-6);
    }

    @Test
    public void testScoreWithDocLengthZeroFreqReturnsZero() {
        // Given
        BM25 instance = new BM25();
        // When
        double result = instance.score(0.0, 100, 150, 10_000_000, 1_000);
        // Then
        assertEquals(0.0, result, 1e-9);
    }

    @Test
    public void testScoreNoLengthNormalisationBIsZero() {
        // Given: b = 0 means docSize has no effect
        BM25 bm25a = new BM25(2.0, 0.0, 0.0);
        // When: same freq, different doc sizes
        double r100 = bm25a.score(3, 100, 150, 10_000_000, 1_000);
        double r500 = bm25a.score(3, 500, 150, 10_000_000, 1_000);
        // Then: scores should be equal when b = 0
        assertEquals(r100, r500, 1e-9);
    }

    @Test
    public void testScoreMonotonicInFrequency() {
        // Given
        BM25 instance = new BM25(2.0, 0.75, 0.0);
        int docSize = 100;
        double avgDocSize = 150;
        long N = 10_000_000, n = 1_000;
        // When
        double r1 = instance.score(1, docSize, avgDocSize, N, n);
        double r3 = instance.score(3, docSize, avgDocSize, N, n);
        double r10 = instance.score(10, docSize, avgDocSize, N, n);
        // Then: higher frequency → higher score
        assertTrue(r1 < r3);
        assertTrue(r3 < r10);
    }

    @Test
    public void testScoreSaturatesAsFrequencyGrowsLarge() {
        // Given: BM25 TF component saturates
        BM25 instance = new BM25(2.0, 0.75, 0.0);
        int docSize = 100;
        double avgDocSize = 150;
        long N = 10_000_000, n = 1_000;
        // When
        double r100  = instance.score(100,    docSize, avgDocSize, N, n);
        double r1000 = instance.score(1000,   docSize, avgDocSize, N, n);
        double r1e6  = instance.score(1_000_000, docSize, avgDocSize, N, n);
        // Then: differences shrink (saturation)
        double gap1 = r1000 - r100;
        double gap2 = r1e6 - r1000;
        assertTrue(gap2 < gap1);
    }

    @Test
    public void testScoreMonotonicInRarity() {
        // Given: rarer term → higher IDF
        BM25 instance = new BM25(2.0, 0.75, 0.0);
        int docSize = 100;
        double avgDocSize = 150;
        long N = 10_000_000;
        // When
        double rare   = instance.score(3, docSize, avgDocSize, N, 10);
        double common = instance.score(3, docSize, avgDocSize, N, 5_000_000);
        // Then
        assertTrue(rare > common);
    }

    @Test
    public void testScoreDeltaIncreasesScore() {
        // Given: BM25 vs BM25+ (delta > 0)
        BM25 bm25  = new BM25(2.0, 0.75, 0.0);
        BM25 bm25p = new BM25(2.0, 0.75, 1.0);
        // When
        double s  = bm25.score(3,  100, 150, 10_000_000, 1_000);
        double sp = bm25p.score(3, 100, 150, 10_000_000, 1_000);
        // Then: BM25+ score should be higher
        assertTrue(sp > s);
    }

    // -----------------------------------------------------------------------
    // score(double freq, long N, long n)  – length-agnostic overload
    // -----------------------------------------------------------------------

    @Test
    public void testScoreNoDocLengthZeroFreqReturnsZero() {
        // Given
        BM25 instance = new BM25();
        // When
        double result = instance.score(0.0, 10_000_000, 1_000);
        // Then
        assertEquals(0.0, result, 1e-9);
    }

    @Test
    public void testScoreNoDocLengthPositiveFreq() {
        // Given
        BM25 instance = new BM25(2.0, 0.75, 0.0);
        // When
        double result = instance.score(3.0, 10_000_000, 1_000);
        // Then: tf = (k1+1)*freq / (freq + k1) = 3*3/(3+2) = 9/5 = 1.8
        double tf = (2.0 + 1) * 3.0 / (3.0 + 2.0);
        double idf = Math.log((10_000_000 - 1_000 + 0.5) / (1_000 + 0.5) + 1);
        assertEquals((tf + 0.0) * idf, result, 1e-9);
    }
}