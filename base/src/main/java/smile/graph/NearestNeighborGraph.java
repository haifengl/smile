/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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

package smile.graph;

import java.util.*;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.distance.Distance;

/**
 * The k-nearest neighbor graph builder.
 *
 * @param neighbors The indices of k-nearest neighbors.
 * @param distances The distances to k-nearest neighbors.
 * @param index The sample index of each vertex in original dataset.
 * @author Haifeng Li
 */
public record NearestNeighborGraph(int[][] neighbors, double[][] distances, int[] index) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NearestNeighborGraph.class);

    /**
     * Constructor.
     * @param neighbors The indices of k-nearest neighbors.
     * @param distances The distances to k-nearest neighbors.
     */
    public NearestNeighborGraph(int[][] neighbors, double[][] distances) {
        this(neighbors, distances, IntStream.range(0, neighbors.length).toArray());
    }

    /**
     * Returns the nearest neighbor graph.
     * @param digraph create a directed graph if true.
     * @return the nearest neighbor graph.
     */
    public AdjacencyList graph(boolean digraph) {
        int n = neighbors.length;
        AdjacencyList graph = new AdjacencyList(n, digraph);
        IntStream.range(0, n).forEach(i -> {
            int[] neighbor = neighbors[i];
            double[] distance = distances[i];
            for (int j = 0; j < neighbor.length; j++) {
                graph.setWeight(i, neighbor[j], distance[j]);
            }
        });
        return graph;
    }

    /**
     * Creates a nearest neighbor graph with Euclidean distance.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @return k-nearest neighbor graph.
     */
    public static NearestNeighborGraph of(double[][] data, int k) {
        return of(data, MathEx::distance, k);
    }

    /**
     * Returns the largest connected component of a nearest neighbor graph.
     *
     * @param digraph create a directed graph if true.
     * @return the largest connected component.
     */
    public NearestNeighborGraph largest(boolean digraph) {
        AdjacencyList graph = graph(digraph);
        int[][] cc = graph.bfcc();
        if (cc.length == 1) {
            return this;
        } else {
            int[] index = Arrays.stream(cc)
                    .max(Comparator.comparing(a -> a.length))
                    .orElseThrow(NoSuchElementException::new);
            logger.info("{} connected components, largest one has {} samples.", cc.length, index.length);

            int n = index.length;
            int k = neighbors[0].length;

            int[] reverseIndex = new int[neighbors.length];
            for (int i = 0; i < n; i++) {
                reverseIndex[index[i]] = i;
            }

            int[][] nearest = new int[n][k];
            double[][] dist = new double[n][k];
            for (int i = 0; i < n; i++) {
                dist[i] = distances[index[i]];
                int[] ni = neighbors[index[i]];
                for (int j = 0; j < k; j++) {
                    nearest[i][j] = reverseIndex[ni[j]];
                }
            }
            return new NearestNeighborGraph(nearest, dist, index);
        }
    }

    private static class Neighbor implements Comparable<Neighbor> {
        public int index;
        public double distance;

        public Neighbor(int index, double distance) {
            this.index = index;
            this.distance = distance;
        }

        @Override
        public int compareTo(Neighbor o) {
            return Double.compare(o.distance, distance);
        }
    }

    /**
     * Creates a nearest neighbor graph.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @param distance the distance function.
     * @return k-nearest neighbor graph.
     */
    public static <T> NearestNeighborGraph of(T[] data, Distance<T> distance, int k) {
        if (k < 2) {
            throw new IllegalArgumentException("k must be greater than 1: " + k);
        }

        int n = data.length;
        int[][] neighbors = new int[n][k];
        double[][] distances = new double[n][k];

        IntStream.range(0, n).parallel().forEach(i -> {
            T xi = data[i];
            PriorityQueue<Neighbor> pq = new PriorityQueue<>();
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                double d = distance.d(xi, data[j]);
                if (pq.size() < k) {
                    pq.offer(new Neighbor(j, d));
                } else if (d < pq.peek().distance) {
                    Neighbor neighbor = pq.poll();
                    neighbor.index = j;
                    neighbor.distance = d;
                    pq.offer(neighbor);
                }
            }

            for (int j = pq.size()-1; !pq.isEmpty(); j--) {
                Neighbor neighbor = pq.poll();
                neighbors[i][j] = neighbor.index;
                distances[i][j] = neighbor.distance;
            }
        });

        return new NearestNeighborGraph(neighbors, distances);
    }

    /**
     * Creates an approximate nearest neighbor graph with Euclidean distance.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @return approximate k-nearest neighbor graph.
     */
    /*public static NearestNeighborGraph descent(double[][] data, int k) {
        return descent(data, MathEx::distance, k);
    }*/

    /**
     * Creates an approximate nearest neighbor graph with the NN-Descent algorithm.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @param distance the distance function.
     * @param maxCandidates the maximum number of candidates in nearest neighbor search.
     * @param maxIter the maximum number of iterations.
     * @return approximate k-nearest neighbor graph.
     */
    /*public static <T> NearestNeighborGraph descent(T[] data, Distance<T> distance, int k, int maxCandidates, int maxIter, double delta, double rho) {
        if (k < 2) {
            throw new IllegalArgumentException("k must be greater than 1: " + k);
        }

        int n = data.length;
        int[][] neighbors = new int[n][k];
        double[][] distances = new double[n][k];
        boolean[][] isNew = new boolean[n][k];
        for (int i = 0; i < n; i++) {
            Arrays.fill(neighbors[i], -1);
            Arrays.fill(distances[i], Double.POSITIVE_INFINITY);
        }

        for (int i = 0; i < n; i++) {
            final float[] iRow = data.row(i);
            for (final int index : Utils.rejectionSample(nNeighbors, data.rows(), random)) {
                final float d = mMetric.distance(iRow, data.row(index));
                currentGraph.push(i, d, index, true);
                currentGraph.push(index, d, i, true);
            }
        }

        if (rpTreeInit) {
            for (final FlatTree tree : forest) {
                for (final int[] leaf : tree.getIndices()) {
                    for (int i = 0; i < leaf.length; ++i) {
                        final float[] iRow = data.row(leaf[i]);
                        for (int j = i + 1; j < leaf.length; ++j) {
                            final float d = mMetric.distance(iRow, data.row(leaf[j]));
                            currentGraph.push(leaf[i], d, leaf[j], true);
                            currentGraph.push(leaf[j], d, leaf[i], true);
                        }
                    }
                }
            }
        }

        boolean[] rejectStatus = new boolean[maxCandidates];
        for (int iter = 0; iter < maxIter; iter++) {
            logger.info("NearestNeighborDescent: {} / {}", (n + 1), maxIter);

            final Heap candidateNeighbors = currentGraph.buildCandidates(n, k, maxCandidates);

            int count = 0;
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < maxCandidates; ++j) {
                    rejectStatus[j] = MathEx.random() < rho;
                }

                for (int j = 0; j < maxCandidates; ++j) {
                    final int p = candidateNeighbors.index(i, j);
                    if (p < 0) {
                        continue;
                    }
                    for (int l = 0; l <= j; l++) {
                        final int q = candidateNeighbors.index(i, l);
                        if (q < 0 || (rejectStatus[j] && rejectStatus[l]) || (!candidateNeighbors.isNew(i, j) && !candidateNeighbors.isNew(i, l))) {
                            continue;
                        }

                        final float d = mMetric.distance(data.row(p), data.row(q));
                        if (currentGraph.push(p, d, q, true)) {
                            ++count;
                        }
                        if (currentGraph.push(q, d, p, true)) {
                            ++count;
                        }
                    }
                }
            }

            if (count <= delta * k * n) {
                break;
            }
        }

        return currentGraph.deheapSort();
        return new NearestNeighborGraph(neighbors, distances);
    }*/
}
