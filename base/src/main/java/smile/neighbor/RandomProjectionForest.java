/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import smile.math.MathEx;
import smile.sort.HeapSelect;

/**
 * A set of random projection trees.
 *
 * @author Karl Li
 */
public class RandomProjectionForest implements KNNSearch<double[], double[]> {
    /** Threshold for nearly zero comparison. */
    private static final double EPS = 1E-8;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RandomProjectionForest.class);
    private final List<FlatTree> trees;
    private final double[][] data;
    private final boolean angular;

    private RandomProjectionForest(List<FlatTree> trees, double[][] data, boolean angular) {
        this.trees = trees;
        this.data = data;
        this.angular = angular;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Neighbor<double[], double[]>[] search(double[] q, int k) {
        HeapSelect<NeighborBuilder<double[], double[]>> heap = new HeapSelect<>(NeighborBuilder.class, k);
        for (int i = 0; i < k; i++) {
            heap.add(new NeighborBuilder<>());
        }

        for (var tree : trees) {
            int[] samples = tree.search(q);
            for (var index : samples) {
                double[] x = data[index];
                double dist = angular ? 1 - MathEx.cosine(q, x) : MathEx.distance(q, x);

                if (heap.size() < k) {
                    heap.add(new NeighborBuilder<>(x, x, index, dist));
                } else if (dist < heap.peek().distance) {
                    var top = heap.peek();
                    top.distance = dist;
                    top.index = index;
                    top.key = x;
                    top.value = x;
                    heap.heapify();
                }
            }
        }

        heap.sort();
        return Arrays.stream(heap.toArray()).map(NeighborBuilder::toNeighbor).toArray(Neighbor[]::new);
    }

    record FlatTree(double[][] hyperplanes, double[] offsets, int[][] children, int[][] indices) {
        public int[] search(double[] point) {
            int node = 0;
            while (children[node][0] > 0) {
                boolean rightSide = RandomProjectionTree.isRightSide(point, hyperplanes[node], offsets[node]);
                node = children[node][rightSide ? 1 : 0];
            }
            return indices[-children[node][0]];
        }
    }

    /**
     * Builds a random projection forest.
     * @param data the data set.
     * @param numTrees the number of trees.
     * @param leafSize The maximum size of leaf node.
     * @param angular true for cosine metric, otherwise Euclidean.
     * @return random projection forest
     */
    public static RandomProjectionForest of(double[][] data, int numTrees, int leafSize, boolean angular) {
        ArrayList<FlatTree> trees = new ArrayList<>();
        for (int i = 0; i < numTrees; i++) {
            RandomProjectionTree tree = RandomProjectionTree.of(data, leafSize, angular);
            trees.add(tree.flatten());
        }
        return new RandomProjectionForest(trees, data, angular);
    }
}