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
 * Edge-case and additional unit tests for {@link KDTree}.
 *
 * @author Haifeng Li
 */
public class KDTreeEdgeTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testSize() {
        double[][] data = MathEx.randn(200, 5);
        KDTree<double[]> tree = KDTree.of(data);
        assertEquals(200, tree.size());
    }

    @Test
    public void testSinglePoint() {
        double[][] data = {{1.0, 2.0, 3.0}};
        KDTree<double[]> tree = KDTree.of(data);
        assertEquals(1, tree.size());

        // Query with a different point – should find the only point
        double[] q = {1.1, 2.1, 3.1};
        Neighbor<double[], double[]>[] neighbors = tree.search(q, 1);
        assertEquals(1, neighbors.length);
        assertEquals(0, neighbors[0].index());
    }

    @Test
    public void testTwoPoints() {
        double[][] data = {{0.0, 0.0}, {1.0, 0.0}};
        KDTree<double[]> tree = KDTree.of(data);

        // nearest to origin among non-identical points
        Neighbor<double[], double[]> n = tree.nearest(data[0]);
        assertEquals(1, n.index());
        assertEquals(1.0, n.distance(), 1e-9);

        n = tree.nearest(data[1]);
        assertEquals(0, n.index());
        assertEquals(1.0, n.distance(), 1e-9);
    }

    @Test
    public void testDuplicatePoints() {
        // All points are identical – every node should still have distance 0
        // to all others; tree should handle zero-spread without infinite loop.
        double[][] data = new double[50][4];
        for (double[] row : data) java.util.Arrays.fill(row, 3.14);
        KDTree<double[]> tree = KDTree.of(data);
        assertEquals(50, tree.size());

        double[] q = {3.14, 3.14, 3.14, 3.14};
        List<Neighbor<double[], double[]>> results = new ArrayList<>();
        tree.search(q, 0.0001, results);
        // all 50 identical points are distance-0 from q, but reference check
        // means q itself (if in data) is excluded; here q is a distinct object
        assertEquals(50, results.size());
    }

    @Test
    public void testInvalidK() {
        double[][] data = MathEx.randn(10, 3);
        KDTree<double[]> tree = KDTree.of(data);
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], 0));
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], -1));
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], 100));
    }

    @Test
    public void testInvalidRadius() {
        double[][] data = MathEx.randn(10, 3);
        KDTree<double[]> tree = KDTree.of(data);
        List<Neighbor<double[], double[]>> results = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], 0.0, results));
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], -1.0, results));
    }

    @Test
    public void testKeyValueConstructor() {
        MathEx.setSeed(19650218);
        double[][] keys = MathEx.randn(50, 3);
        String[] values = new String[50];
        for (int i = 0; i < 50; i++) values[i] = "item" + i;

        KDTree<String> tree = new KDTree<>(keys, values);
        LinearSearch<double[], String> linear = new LinearSearch<>(keys, values, MathEx::distance);
        assertEquals(50, tree.size());

        double[] q = MathEx.randn(1, 3)[0];
        Neighbor<double[], String>[] kdNb   = tree.search(q, 3);
        Neighbor<double[], String>[] linNb  = linear.search(q, 3);
        assertEquals(3, kdNb.length);
        for (int i = 0; i < 3; i++) {
            assertEquals(linNb[i].index(), kdNb[i].index());
            assertEquals(linNb[i].value(), kdNb[i].value());
        }
    }

    @Test
    public void testRangeReturnsSortedByDistanceAfterSort() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(200, 3);
        KDTree<double[]> tree = KDTree.of(data);
        double[] q = MathEx.randn(1, 3)[0];

        List<Neighbor<double[], double[]>> results = new ArrayList<>();
        tree.search(q, 2.0, results);
        results.sort(null);

        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).distance() <= results.get(i).distance());
        }
    }

    @Test
    public void testKnnSearchListDefault() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(100, 4);
        KDTree<double[]> tree = KDTree.of(data);

        List<Neighbor<double[], double[]>> list = new ArrayList<>();
        tree.search(data[5], 5, list);
        assertEquals(5, list.size());
    }

    @Test
    public void testKnnMatchesLinear() {
        MathEx.setSeed(42);
        double[][] data = MathEx.randn(300, 5);
        KDTree<double[]> tree = KDTree.of(data);
        LinearSearch<double[], double[]> linear = LinearSearch.of(data, MathEx::distance);

        for (int i = 0; i < 20; i++) {
            double[] q = MathEx.randn(1, 5)[0];
            Neighbor<double[], double[]>[] expected = linear.search(q, 5);
            Neighbor<double[], double[]>[] actual   = tree.search(q, 5);
            for (int j = 0; j < 5; j++) {
                assertEquals(expected[j].index(), actual[j].index());
                assertEquals(expected[j].distance(), actual[j].distance(), 1e-9);
            }
        }
    }

    @Test
    public void testHighDimensionalData() {
        // KD-tree degrades in high dim but must still return correct results
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(500, 30);
        KDTree<double[]> tree = KDTree.of(data);
        LinearSearch<double[], double[]> linear = LinearSearch.of(data, MathEx::distance);

        double[] q = MathEx.randn(1, 30)[0];
        Neighbor<double[], double[]> n1 = linear.nearest(q);
        Neighbor<double[], double[]> n2 = tree.nearest(q);
        assertEquals(n1.index(), n2.index());
        assertEquals(n1.distance(), n2.distance(), 1e-9);
    }
}

