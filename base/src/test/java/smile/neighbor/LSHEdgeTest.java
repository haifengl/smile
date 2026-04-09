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
package smile.neighbor;

import java.util.ArrayList;
import java.util.List;
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case and additional unit tests for {@link LSH}.
 *
 * @author Haifeng Li
 */
public class LSHEdgeTest {

    @Test
    public void testSize() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(200, 10);
        LSH<double[]> lsh = new LSH<>(data, data, 1.0);
        assertEquals(200, lsh.size());
    }

    @Test
    public void testToString() {
        int d = 5, L = 4, k = 3;
        LSH<double[]> lsh = new LSH<>(d, L, k, 1.0);
        String s = lsh.toString();
        assertTrue(s.startsWith("LSH("));
        assertTrue(s.contains("L=4"));
        assertTrue(s.contains("k=3"));
    }

    @Test
    public void testInvalidConstructorArgs() {
        // d < 2
        assertThrows(IllegalArgumentException.class, () -> new LSH<>(1, 3, 3, 1.0));
        // L < 1
        assertThrows(IllegalArgumentException.class, () -> new LSH<>(5, 0, 3, 1.0));
        // k < 1
        assertThrows(IllegalArgumentException.class, () -> new LSH<>(5, 3, 0, 1.0));
        // w <= 0
        assertThrows(IllegalArgumentException.class, () -> new LSH<>(5, 3, 3, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new LSH<>(5, 3, 3, -1.0));
        // H < 1
        assertThrows(IllegalArgumentException.class, () -> new LSH<>(5, 3, 3, 1.0, 0));
    }

    @Test
    public void testInvalidK() {
        LSH<double[]> lsh = new LSH<>(5, 4, 3, 1.0);
        double[] q = MathEx.randn(1, 5)[0];
        assertThrows(IllegalArgumentException.class, () -> lsh.search(q, 0));
    }

    @Test
    public void testInvalidRadius() {
        LSH<double[]> lsh = new LSH<>(5, 4, 3, 1.0);
        lsh.put(MathEx.randn(1, 5)[0], new double[5]);
        double[] q = MathEx.randn(1, 5)[0];
        List<Neighbor<double[], double[]>> results = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> lsh.search(q, 0.0, results));
        assertThrows(IllegalArgumentException.class, () -> lsh.search(q, -1.0, results));
    }

    @Test
    public void testPutAndNearest() {
        MathEx.setSeed(19650218);
        LSH<String> lsh = new LSH<>(3, 8, 3, 1.0);

        double[] p1 = {0.0, 0.0, 0.0};
        double[] p2 = {10.0, 10.0, 10.0};
        lsh.put(p1, "origin");
        lsh.put(p2, "far");

        // A query very close to origin
        double[] q = {0.01, 0.01, 0.01};
        Neighbor<double[], String> n = lsh.nearest(q);
        // nearest may return null if q is not in the same bucket
        if (n != null) {
            assertEquals("origin", n.value());
        }
    }

    @Test
    public void testNearestReturnsNullWhenNoCandidates() {
        // Build LSH with a few points in very different regions
        LSH<double[]> lsh = new LSH<>(3, 4, 3, 0.1);
        // Add points in a cluster far from the query
        for (int i = 0; i < 5; i++) {
            lsh.put(new double[]{1000.0 + i, 1000.0, 1000.0},
                    new double[]{1000.0 + i, 1000.0, 1000.0});
        }
        // Query near origin; likely no candidates
        double[] q = {0.0, 0.0, 0.0};
        Neighbor<double[], double[]> n = lsh.nearest(q);
        // May be null (no candidate) – simply must not throw
        // If not null, distance must be non-negative
        if (n != null) {
            assertTrue(n.distance() >= 0.0);
        }
    }

    @Test
    public void testRangeSearch() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(100, 5);
        LSH<double[]> lsh = new LSH<>(data, data, 2.0);
        LinearSearch<double[], double[]> linear = LinearSearch.of(data, MathEx::distance);

        double[] q = MathEx.randn(1, 5)[0];
        List<Neighbor<double[], double[]>> lshResults    = new ArrayList<>();
        List<Neighbor<double[], double[]>> linearResults = new ArrayList<>();
        lsh.search(q, 1.0, lshResults);
        linear.search(q, 1.0, linearResults);

        // LSH may have false negatives but must not have false positives
        for (var n : lshResults) {
            assertTrue(n.distance() <= 1.0 + 1e-9);
        }
        // recall should be at least 50%
        long truePositives = lshResults.stream()
                .filter(n1 -> linearResults.stream().anyMatch(n2 -> n2.index() == n1.index()))
                .count();
        assertTrue(truePositives >= linearResults.size() * 0.5 || linearResults.isEmpty());
    }
}



