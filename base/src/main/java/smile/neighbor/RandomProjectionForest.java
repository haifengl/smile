/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

import java.util.*;
import java.util.stream.IntStream;
import smile.graph.NearestNeighborGraph;
import smile.math.MathEx;
import smile.sort.HeapSelect;

/**
 * A set of random projection trees.
 *
 * @author Karl Li
 */
public class RandomProjectionForest implements KNNSearch<double[], double[]> {
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

        Set<Integer> uniqueSamples = new HashSet<>();
        for (var tree : trees) {
            int[] samples = tree.search(q);
            for (var index : samples) {
                uniqueSamples.add(index);
            }
        }

        for (var index : uniqueSamples) {
            double[] x = data[index];
            double dist = angular ? MathEx.angular(q, x) : MathEx.distance(q, x);

            if (heap.size() < k) {
                heap.add(new NeighborBuilder<>(x, x, index, dist));
            } else {
                var top = heap.peek();
                if (dist < top.distance) {
                    top.distance = dist;
                    top.index = index;
                    top.key = x;
                    top.value = x;
                    heap.siftDown();
                }
            }
        }

        heap.sort();
        return Arrays.stream(heap.toArray()).map(NeighborBuilder::toNeighbor).toArray(Neighbor[]::new);
    }

    /**
     * Returns a k-nearest neighbor graph.
     * @param k k-nearest neighbors.
     * @return k-nearest neighbor graph.
     */
    public NearestNeighborGraph toGraph(int k) {
        int n = data.length;
        List<HeapSelect<NeighborBuilder<double[], double[]>>> heapList = new ArrayList<>(n);
        List<Set<Integer>> neighborSetList = new ArrayList<>(n);
        for (int i = 0; i < data.length; i++) {
            heapList.add(new HeapSelect<>(NeighborBuilder.class, k));
            neighborSetList.add(new HashSet<>());
        }

        for (var tree : trees) {
            for (int[] leaf : tree.indices) {
                for (int li = 0; li < leaf.length; li++) {
                    int i = leaf[li];
                    double[] xi = data[i];
                    for (int lj = li + 1; lj < leaf.length; lj++) {
                        int j = leaf[lj];
                        double[] xj = data[j];
                        double dist = angular ? MathEx.angular(xi, xj) : MathEx.distance(xi, xj);

                        update(neighborSetList.get(i), heapList.get(i), k, xj, j, dist);
                        update(neighborSetList.get(j), heapList.get(j), k, xi, i, dist);
                    }
                }
            }
        }

        int[][] neighbors = new int[n][];
        double[][] distances = new double[n][];
        for (int i = 0; i < n; i++) {
            var pq = heapList.get(i);
            int m = Math.min(k, pq.size());
            neighbors[i] = new int[m];
            distances[i] = new double[m];

            pq.sort();
            var a = pq.toArray();
            for (int j = 0, l = m-1; j < m; j++, l--) {
                neighbors[i][j] = a[l].index;
                distances[i][j] = a[l].distance;
            }
        }
        return new NearestNeighborGraph(k, neighbors, distances);
    }

    private static void update(Set<Integer> set, HeapSelect<NeighborBuilder<double[], double[]>> pq, int k, double[] x, int index, double dist) {
        if (!set.contains(index)) {
            if (pq.size() < k) {
                pq.add(new NeighborBuilder<>(x, x, index, dist));
                set.add(index);
            } else {
                var top = pq.peek();
                if (dist < top.distance) {
                    set.remove(top.index);
                    set.add(index);
                    top.distance = dist;
                    top.index = index;
                    top.key = x;
                    top.value = x;
                    pq.siftDown();
                }
            }
        }
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
     * @param angular true for angular metric, otherwise Euclidean.
     * @return random projection forest
     */
    public static RandomProjectionForest of(double[][] data, int numTrees, int leafSize, boolean angular) {
        var trees = IntStream.range(0, numTrees).parallel()
                .mapToObj(i -> RandomProjectionTree.of(data, leafSize, angular).flatten())
                .toList();

        return new RandomProjectionForest(trees, data, angular);
    }
}