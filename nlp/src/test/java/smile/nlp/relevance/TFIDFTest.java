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
 * Tests for {@link TFIDF}.
 *
 * @author Haifeng Li
 */
public class TFIDFTest {

    // -----------------------------------------------------------------------
    // Constructor & accessors
    // -----------------------------------------------------------------------

    @Test
    public void testDefaultSmoothing() {
        // Given: default constructor
        TFIDF tfidf = new TFIDF();
        // When / Then: smoothing should be 0.4
        assertEquals(0.4, tfidf.smoothing(), 1e-9);
    }

    @Test
    public void testCustomSmoothing() {
        // Given: custom smoothing value
        TFIDF tfidf = new TFIDF(0.5);
        // When / Then
        assertEquals(0.5, tfidf.smoothing(), 1e-9);
    }

    @Test
    public void testSmoothingBoundaryZero() {
        // Given: smoothing at lower bound
        // When / Then: should not throw
        assertDoesNotThrow(() -> new TFIDF(0.0));
    }

    @Test
    public void testSmoothingBoundaryOne() {
        // Given: smoothing at upper bound
        // When / Then: should not throw
        assertDoesNotThrow(() -> new TFIDF(1.0));
    }

    @Test
    public void testSmoothingNegativeThrows() {
        // Given: negative smoothing
        // When / Then: should throw
        assertThrows(IllegalArgumentException.class, () -> new TFIDF(-0.1));
    }

    @Test
    public void testSmoothingAboveOneThrows() {
        // Given: smoothing > 1
        // When / Then: should throw
        assertThrows(IllegalArgumentException.class, () -> new TFIDF(1.1));
    }

    // -----------------------------------------------------------------------
    // rank(int tf, int maxtf, long N, long n)
    // -----------------------------------------------------------------------

    @Test
    public void testRankBaseline() {
        // Given: standard corpus parameters
        TFIDF instance = new TFIDF();
        // When: term appears 3 times, maxtf=10, 10M docs, 1000 containing term
        double result = instance.rank(3, 10, 10_000_000, 1_000);
        // Then: (0.4 + 0.6*3/10) * ln(10_000_000/1_000) = 0.58 * ln(10_000)
        double expected = (0.4 + 0.6 * 3.0 / 10) * Math.log(10_000_000.0 / 1_000);
        assertEquals(expected, result, 1e-6);
    }

    @Test
    public void testRankZeroTermFrequencyReturnsZero() {
        // Given: term does not appear in the document
        TFIDF instance = new TFIDF();
        // When
        double result = instance.rank(0, 10, 10_000_000, 1_000);
        // Then
        assertEquals(0.0, result, 1e-9);
    }

    @Test
    public void testRankMaxTermFrequencyEqualsFrequency() {
        // Given: term is the most frequent in the document (tf == maxtf)
        TFIDF instance = new TFIDF();
        // When
        double result = instance.rank(5, 5, 10_000_000, 1_000);
        // Then: normalised tf = a + (1-a)*1 = 1.0
        double expected = Math.log(10_000_000.0 / 1_000);
        assertEquals(expected, result, 1e-6);
    }

    @Test
    public void testRankSmoothingZeroEqualsRawIDF() {
        // Given: a = 0  →  tf_norm = tf/maxtf (no smoothing)
        TFIDF instance = new TFIDF(0.0);
        // When
        double result = instance.rank(3, 10, 10_000_000, 1_000);
        // Then: tf_norm = 0.3
        double expected = 0.3 * Math.log(10_000_000.0 / 1_000);
        assertEquals(expected, result, 1e-6);
    }

    @Test
    public void testRankSmoothingOneGivesFlatTF() {
        // Given: a = 1  →  tf_norm is always 1 regardless of tf/maxtf
        TFIDF instance = new TFIDF(1.0);
        // When
        double result1 = instance.rank(1, 10, 10_000_000, 1_000);
        double result5 = instance.rank(5, 10, 10_000_000, 1_000);
        // Then: both should be equal
        assertEquals(result1, result5, 1e-9);
    }

    @Test
    public void testRankHighDFLowIDF() {
        // Given: term appears in half the documents (common term)
        TFIDF instance = new TFIDF();
        // When
        double result = instance.rank(3, 10, 1_000, 500);
        // Then: idf = ln(1000/500) = ln(2) ≈ 0.693
        double tfNorm = 0.4 + 0.6 * 3.0 / 10;
        double expected = tfNorm * Math.log(2.0);
        assertEquals(expected, result, 1e-6);
    }

    @Test
    public void testRankMonotonicInTermFrequency() {
        // Given: fixed corpus
        TFIDF instance = new TFIDF();
        // When
        double r1 = instance.rank(1, 10, 10_000_000, 1_000);
        double r5 = instance.rank(5, 10, 10_000_000, 1_000);
        double r10 = instance.rank(10, 10, 10_000_000, 1_000);
        // Then: higher tf → higher score
        assertTrue(r1 < r5);
        assertTrue(r5 < r10);
    }

    @Test
    public void testRankMonotonicInRarity() {
        // Given: same term frequency, rare term vs common term
        TFIDF instance = new TFIDF();
        // When
        double rare   = instance.rank(3, 10, 10_000_000,    10);
        double common = instance.rank(3, 10, 10_000_000, 5_000_000);
        // Then: rarer term → higher IDF → higher score
        assertTrue(rare > common);
    }
}