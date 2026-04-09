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
 * Edge-case and additional unit tests for {@link CoverTree}.
 *
 * @author Haifeng Li
 */
public class CoverTreeEdgeTest {

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testSize() {
        double[][] data = MathEx.randn(150, 5);
        CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance);
        assertEquals(150, tree.size());
    }

    @Test
    public void testSinglePoint() {
        double[][] data = {{1.0, 2.0, 3.0}};
        CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance);
        assertEquals(1, tree.size());

        double[] q = {1.5, 2.5, 3.5};
        Neighbor<double[], double[]>[] neighbors = tree.search(q, 1);
        assertEquals(1, neighbors.length);
        assertEquals(0, neighbors[0].index());
    }

    @Test
    public void testInvalidK() {
        double[][] data = MathEx.randn(10, 3);
        CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance);
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], 0));
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], -1));
    }

    @Test
    public void testInvalidRadius() {
        double[][] data = MathEx.randn(10, 3);
        CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance);
        List<Neighbor<double[], double[]>> results = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], 0.0, results));
        assertThrows(IllegalArgumentException.class, () -> tree.search(data[0], -1.0, results));
    }

    @Test
    public void testKnnMatchesLinear() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(300, 5);
        CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance);
        LinearSearch<double[], double[]> linear = LinearSearch.of(data, MathEx::distance);

        for (int i = 0; i < 30; i++) {
            double[] q = MathEx.randn(1, 5)[0];
            Neighbor<double[], double[]>[] expected = linear.search(q, 5);
            Neighbor<double[], double[]>[] actual   = tree.search(q, 5);
            for (int j = 0; j < 5; j++) {
                assertEquals(expected[j].index(), actual[j].index(),
                        "Mismatch at query " + i + " rank " + j);
                assertEquals(expected[j].distance(), actual[j].distance(), 1e-9);
            }
        }
    }

    @Test
    public void testRangeMatchesLinear() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(200, 4);
        CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance);
        LinearSearch<double[], double[]> linear = LinearSearch.of(data, MathEx::distance);

        double[] q = MathEx.randn(1, 4)[0];
        List<Neighbor<double[], double[]>> treeRes   = new ArrayList<>();
        List<Neighbor<double[], double[]>> linearRes = new ArrayList<>();
        tree.search(q, 1.5, treeRes);
        linear.search(q, 1.5, linearRes);
        treeRes.sort(null);
        linearRes.sort(null);

        assertEquals(linearRes.size(), treeRes.size());
        for (int i = 0; i < linearRes.size(); i++) {
            assertEquals(linearRes.get(i).index(), treeRes.get(i).index());
        }
    }

    @Test
    public void testCustomBase() {
        MathEx.setSeed(42);
        double[][] data = MathEx.randn(100, 3);
        // base = 2.0 instead of default 1.3
        CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance, 2.0);
        LinearSearch<double[], double[]> linear = LinearSearch.of(data, MathEx::distance);

        double[] q = MathEx.randn(1, 3)[0];
        Neighbor<double[], double[]> n1 = linear.nearest(q);
        Neighbor<double[], double[]> n2 = tree.nearest(q);
        assertEquals(n1.index(), n2.index());
        assertEquals(n1.distance(), n2.distance(), 1e-9);
    }

    @Test
    public void testListConstructor() {
        MathEx.setSeed(19650218);
        double[][] arr = MathEx.randn(50, 3);
        java.util.List<double[]> list = java.util.Arrays.asList(arr);
        CoverTree<double[], double[]> tree = CoverTree.of(list, MathEx::distance);
        assertEquals(50, tree.size());
    }

    @Test
    public void testKnnSearchListDefault() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(100, 4);
        CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance);

        List<Neighbor<double[], double[]>> list = new ArrayList<>();
        tree.search(data[5], 5, list);
        assertEquals(5, list.size());
    }
}

