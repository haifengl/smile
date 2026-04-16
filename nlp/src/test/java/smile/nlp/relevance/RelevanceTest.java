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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import smile.nlp.Text;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Relevance}.
 *
 * @author Haifeng Li
 */
public class RelevanceTest {

    private static final Text DOC_A = new Text("doc-a", "Title A", "body a");
    private static final Text DOC_B = new Text("doc-b", "Title B", "body b");

    // -----------------------------------------------------------------------
    // Record construction & accessors
    // -----------------------------------------------------------------------

    @Test
    public void testRecordAccessors() {
        // Given
        Relevance rel = new Relevance(DOC_A, 3.14);
        // When / Then
        assertSame(DOC_A, rel.text());
        assertEquals(3.14, rel.score(), 1e-9);
    }

    @Test
    public void testRecordEquality() {
        // Given
        Relevance r1 = new Relevance(DOC_A, 2.5);
        Relevance r2 = new Relevance(DOC_A, 2.5);
        // When / Then: records with equal components are equal
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testRecordInequalityDifferentScore() {
        // Given
        Relevance r1 = new Relevance(DOC_A, 2.5);
        Relevance r2 = new Relevance(DOC_A, 5.0);
        // When / Then
        assertNotEquals(r1, r2);
    }

    // -----------------------------------------------------------------------
    // Comparable / ordering
    // -----------------------------------------------------------------------

    @Test
    public void testCompareToLessThan() {
        // Given
        Relevance low  = new Relevance(DOC_A, 1.0);
        Relevance high = new Relevance(DOC_B, 5.0);
        // When / Then: natural order is ascending (low < high)
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
    }

    @Test
    public void testCompareToEqual() {
        // Given
        Relevance r1 = new Relevance(DOC_A, 3.0);
        Relevance r2 = new Relevance(DOC_B, 3.0);
        // When / Then
        assertEquals(0, r1.compareTo(r2));
    }

    @Test
    public void testNaturalSortAscending() {
        // Given: three results with different scores
        Relevance r1 = new Relevance(DOC_A, 9.0);
        Relevance r2 = new Relevance(DOC_B, 1.0);
        Relevance r3 = new Relevance(new Text("c", "C", "c"), 5.0);
        List<Relevance> list = new ArrayList<>(List.of(r1, r2, r3));
        // When: sort naturally (ascending)
        Collections.sort(list);
        // Then: ascending order
        assertEquals(1.0, list.get(0).score(), 1e-9);
        assertEquals(5.0, list.get(1).score(), 1e-9);
        assertEquals(9.0, list.get(2).score(), 1e-9);
    }

    @Test
    public void testReverseOrderGivesDescendingRanking() {
        // Given: three results with different scores
        Relevance r1 = new Relevance(DOC_A, 9.0);
        Relevance r2 = new Relevance(DOC_B, 1.0);
        Relevance r3 = new Relevance(new Text("c", "C", "c"), 5.0);
        List<Relevance> list = new ArrayList<>(List.of(r1, r2, r3));
        // When: sort in reverse (most-relevant first)
        list.sort(Comparator.reverseOrder());
        // Then: descending order (most relevant first)
        assertEquals(9.0, list.get(0).score(), 1e-9);
        assertEquals(5.0, list.get(1).score(), 1e-9);
        assertEquals(1.0, list.get(2).score(), 1e-9);
    }

    @Test
    public void testNaNScoreComparison() {
        // Given: NaN score (degenerate input)
        Relevance nan    = new Relevance(DOC_A, Double.NaN);
        Relevance normal = new Relevance(DOC_B, 5.0);
        // When / Then: Double.compare handles NaN consistently
        int cmp = nan.compareTo(normal);
        // NaN is considered greater than any double by Double.compare
        assertTrue(cmp > 0);
    }
}


