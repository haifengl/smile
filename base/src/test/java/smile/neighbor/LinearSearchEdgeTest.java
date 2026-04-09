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
 * Edge-case and additional unit tests for {@link LinearSearch}.
 *
 * @author Haifeng Li
 */
public class LinearSearchEdgeTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testSize() {
        double[][] data = MathEx.randn(100, 5);
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);
        assertEquals(100, search.size());
    }

    @Test
    public void testSingleElement() {
        double[][] data = {{1.0, 2.0}};
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);
        assertEquals(1, search.size());

        double[] q = {2.0, 3.0};
        Neighbor<double[], double[]>[] neighbors = search.search(q, 1);
        assertEquals(1, neighbors.length);
        assertEquals(0, neighbors[0].index());
    }

    @Test
    public void testInvalidK() {
        double[][] data = MathEx.randn(10, 3);
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);
        assertThrows(IllegalArgumentException.class, () -> search.search(data[0], 0));
        assertThrows(IllegalArgumentException.class, () -> search.search(data[0], 11));
    }

    @Test
    public void testInvalidRadius() {
        double[][] data = MathEx.randn(10, 3);
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);
        List<Neighbor<double[], double[]>> results = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> search.search(data[0],  0.0, results));
        assertThrows(IllegalArgumentException.class, () -> search.search(data[0], -1.0, results));
    }

    @Test
    public void testNearestExcludesSelf() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(50, 4);
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);

        // Query with reference to a data point – it should be excluded
        for (int i = 0; i < data.length; i++) {
            Neighbor<double[], double[]> n = search.nearest(data[i]);
            assertNotEquals(i, n.index(), "Self should be excluded for index " + i);
        }
    }

    @Test
    public void testKnnSortedByDistance() {
        MathEx.setSeed(42);
        double[][] data = MathEx.randn(100, 3);
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);

        double[] q = MathEx.randn(1, 3)[0];
        Neighbor<double[], double[]>[] neighbors = search.search(q, 10);
        assertEquals(10, neighbors.length);
        // HeapSelect.sort() produces descending order (largest distance first).
        for (int i = 1; i < neighbors.length; i++) {
            assertTrue(neighbors[i - 1].distance() >= neighbors[i].distance(),
                    "Expected descending order at index " + i);
        }
    }

    @Test
    public void testRangeSearchNoResults() {
        double[][] data = {{10.0, 10.0}, {20.0, 20.0}};
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);

        double[] q = {0.0, 0.0};
        List<Neighbor<double[], double[]>> results = new ArrayList<>();
        search.search(q, 1.0, results);   // very small radius
        assertTrue(results.isEmpty());
    }

    @Test
    public void testRangeSearchAllResults() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(50, 2);
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);

        double[] q = {0.0, 0.0};
        List<Neighbor<double[], double[]>> results = new ArrayList<>();
        search.search(q, 1000.0, results);  // radius so large it includes all
        // All points except the query itself (q is a new object so no ref match)
        assertEquals(50, results.size());
    }

    @Test
    public void testKnnListDefaultMethod() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(100, 4);
        LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);

        List<Neighbor<double[], double[]>> list = new ArrayList<>();
        search.search(data[5], 5, list);
        assertEquals(5, list.size());
    }

    @Test
    public void testFunctionKeyConstructor() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(50, 3);
        // Value is a wrapper record; key is extracted by a lambda
        record Pt(double[] vec) {}
        var points = java.util.Arrays.stream(data).map(Pt::new)
                .toArray(Pt[]::new);

        LinearSearch<double[], Pt> search = new LinearSearch<>(
                java.util.Arrays.asList(points), MathEx::distance, Pt::vec);
        assertEquals(50, search.size());

        double[] q = MathEx.randn(1, 3)[0];
        Neighbor<double[], Pt>[] nb = search.search(q, 3);
        assertEquals(3, nb.length);
        for (var n : nb) {
            assertNotNull(n.value());
            assertNotNull(n.key());
        }
    }

    @Test
    public void testListDataConstructor() {
        double[][] data = MathEx.randn(30, 3);
        List<double[]> list = java.util.Arrays.asList(data);
        LinearSearch<double[], double[]> search = LinearSearch.of(list, MathEx::distance);
        assertEquals(30, search.size());

        double[] q = MathEx.randn(1, 3)[0];
        Neighbor<double[], double[]> n = search.nearest(q);
        assertNotNull(n);
    }
}



