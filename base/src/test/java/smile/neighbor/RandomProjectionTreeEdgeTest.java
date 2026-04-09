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

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge-case and additional unit tests for {@link RandomProjectionTree}.
 *
 * @author Haifeng Li
 */
public class RandomProjectionTreeEdgeTest {

    @Test
    public void testNumNodesAndLeaves() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(200, 10);
        RandomProjectionTree tree = RandomProjectionTree.of(data, 10, false);

        int nodes  = tree.numNodes();
        int leaves = tree.numLeaves();
        assertTrue(nodes  > 0, "Tree must have at least one node");
        assertTrue(leaves > 0, "Tree must have at least one leaf");
        assertTrue(nodes  >= leaves, "Total nodes >= leaf nodes");
    }

    @Test
    public void testLeafSamplesNonEmpty() {
        MathEx.setSeed(42);
        double[][] data = MathEx.randn(100, 5);
        int leafSize = 8;
        RandomProjectionTree tree = RandomProjectionTree.of(data, leafSize, false);

        var samples = tree.leafSamples();
        assertEquals(tree.numLeaves(), samples.size());
        for (int[] leaf : samples) {
            assertTrue(leaf.length > 0, "Each leaf must contain at least one sample");
        }
    }

    @Test
    public void testLeafSizeEnforced() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(200, 5);
        int leafSize = 15;
        RandomProjectionTree tree = RandomProjectionTree.of(data, leafSize, false);

        // Each leaf should contain at most leafSize samples
        for (int[] leaf : tree.leafSamples()) {
            assertTrue(leaf.length <= leafSize,
                    "Leaf size exceeded: " + leaf.length + " > " + leafSize);
        }
    }

    @Test
    public void testInvalidLeafSize() {
        double[][] data = MathEx.randn(50, 5);
        // leafSize must be at least 3
        assertThrows(IllegalArgumentException.class,
                () -> RandomProjectionTree.of(data, 2, false));
        assertThrows(IllegalArgumentException.class,
                () -> RandomProjectionTree.of(data, 1, false));
    }

    @Test
    public void testKnnDoesNotExceedLeafSize() {
        MathEx.setSeed(42);
        double[][] data = MathEx.randn(100, 5);
        int leafSize = 10;
        RandomProjectionTree tree = RandomProjectionTree.of(data, leafSize, false);

        double[] q = MathEx.randn(1, 5)[0];
        assertThrows(IllegalArgumentException.class,
                () -> tree.search(q, leafSize + 1),
                "search(q, k) with k > leafSize must throw");
    }

    @Test
    public void testKnnReturnsKNeighbors() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(200, 5);
        int leafSize = 20;
        RandomProjectionTree tree = RandomProjectionTree.of(data, leafSize, false);

        double[] q = MathEx.randn(1, 5)[0];
        int k = 5;
        var neighbors = tree.search(q, k);
        assertTrue(neighbors.length >= 1 && neighbors.length <= k);
    }

    @Test
    public void testAngularTreeStructure() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(100, 8);
        RandomProjectionTree tree = RandomProjectionTree.of(data, 10, true);

        assertTrue(tree.numNodes()  > 0);
        assertTrue(tree.numLeaves() > 0);

        double[] q = MathEx.randn(1, 8)[0];
        var neighbors = tree.search(q, 5);
        assertTrue(neighbors.length >= 1);
        for (var n : neighbors) {
            assertTrue(n.distance() >= 0.0);
        }
    }

    @Test
    public void testSamplesPartitionData() {
        MathEx.setSeed(42);
        double[][] data = MathEx.randn(100, 4);
        RandomProjectionTree tree = RandomProjectionTree.of(data, 10, false);

        // Every data point must appear in exactly one leaf
        var allSamples = new java.util.HashSet<Integer>();
        int totalSamples = 0;
        for (int[] leaf : tree.leafSamples()) {
            for (int idx : leaf) {
                allSamples.add(idx);
                totalSamples++;
            }
        }

        assertEquals(data.length, allSamples.size(),
                "Every data point should appear in exactly one leaf");
        assertEquals(data.length, totalSamples,
                "No data point should be duplicated across leaves");
    }

    @Test
    public void testNeighborDistancesAreNonNegative() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(100, 5);
        RandomProjectionTree tree = RandomProjectionTree.of(data, 10, false);

        double[] q = MathEx.randn(1, 5)[0];
        var neighbors = tree.search(q, 5);
        for (var n : neighbors) {
            assertTrue(n.distance() >= 0.0, "Distance must be non-negative");
        }
    }

    @Test
    public void testNeighborDistancesMatchActual() {
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(100, 5);
        RandomProjectionTree tree = RandomProjectionTree.of(data, 10, false);

        double[] q = MathEx.randn(1, 5)[0];
        var neighbors = tree.search(q, 5);
        for (var n : neighbors) {
            double expected = MathEx.distance(q, data[n.index()]);
            assertEquals(expected, n.distance(), 1e-9,
                    "Stored distance does not match actual distance for index " + n.index());
        }
    }

    @Test
    public void testFlattenAndReuse() {
        // RandomProjectionForest uses flatten() internally; test it via forest
        MathEx.setSeed(19650218);
        double[][] data = MathEx.randn(100, 5);
        var forest = smile.neighbor.RandomProjectionForest.of(data, 3, 10, false);

        double[] q = MathEx.randn(1, 5)[0];
        var neighbors = forest.search(q, 5);
        assertNotNull(neighbors);
        assertTrue(neighbors.length >= 1);
    }
}
