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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.graph;

import java.util.*;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.Metric;
import smile.neighbor.RandomProjectionTree;

/**
 * The k-nearest neighbor graph builder.
 *
 * @param k k-nearest neighbor.
 * @param neighbors The indices of k-nearest neighbors.
 * @param distances The distances to k-nearest neighbors.
 * @param index The sample index of each vertex in original dataset.
 * @author Haifeng Li
 */
public record NearestNeighborGraph(int k, int[][] neighbors, double[][] distances, int[] index) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NearestNeighborGraph.class);

    /**
     * Constructor.
     * @param k k-nearest neighbor.
     * @param neighbors The indices of k-nearest neighbors.
     * @param distances The distances to k-nearest neighbors.
     */
    public NearestNeighborGraph(int k, int[][] neighbors, double[][] distances) {
        this(k, neighbors, distances, IntStream.range(0, neighbors.length).toArray());
    }

    /**
     * Returns the number of vertices.
     * @return the number of vertices.
     */
    public int size() {
        return neighbors.length;
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

            int n = neighbors.length;
            int[] reverseIndex = new int[n];
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
            return new NearestNeighborGraph(k, nearest, dist, index);
        }
    }

    /**
     * Creates a nearest neighbor graph.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @param distance the distance function.
     * @param <T> the type of data objects.
     * @return k-nearest neighbor graph.
     */
    public static <T> NearestNeighborGraph of(T[] data, Distance<T> distance, int k) {
        var heap = build(data, distance, k, (int n, int k_, int i) -> IntStream.range(0, n).toArray());
        return toGraph(heap, k);
    }

    /**
     * Creates a random neighbor graph.
     *
     * @param data the dataset.
     * @param k k-random neighbor.
     * @param distance the distance function.
     * @param <T> the type of data objects.
     * @return k-random neighbor graph.
     */
    public static <T> NearestNeighborGraph random(T[] data, Distance<T> distance, int k) {
        var heap = build(data, distance, k, NearestNeighborGraph::rejectionSample);
        extend(heap);
        return toGraph(heap, k);
    }

    private static class Neighbor implements Comparable<Neighbor> {
        public int index;
        public double distance;

        public Neighbor(int index, double distance) {
            this.index = index;
            this.distance = distance;
        }

        @Override
        public int hashCode() {
            return index;
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
     * @param candidates neighbor candidate generator.
     * @return a list of k-nearest neighbor heaps for each data point.
     */
    private static <T> List<PriorityQueue<Neighbor>> build(T[] data, Distance<T> distance, int k, CandidateGenerator candidates) {
        if (k < 2) {
            throw new IllegalArgumentException("k must be greater than 1: " + k);
        }

        int n = data.length;
        List<PriorityQueue<Neighbor>> heap = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            heap.add(new PriorityQueue<>());
        }

        IntStream.range(0, n).parallel().forEach(i -> {
            T xi = data[i];
            PriorityQueue<Neighbor> pq = heap.get(i);
            for (int j : candidates.generate(n, k, i)) {
                if (j == i) continue;
                double dist = distance.d(xi, data[j]);
                if (pq.size() < k) {
                    pq.offer(new Neighbor(j, dist));
                } else if (dist < pq.peek().distance) {
                    Neighbor neighbor = pq.poll();
                    neighbor.index = j;
                    neighbor.distance = dist;
                    pq.offer(neighbor);
                }
            }
        });

        return heap;
    }

    /** Extends nearest neighbor heap with reverse nearest neighbors. */
    private static void extend(List<PriorityQueue<Neighbor>> heap) {
        int n = heap.size();
        List<Set<Integer>> neighbors = new ArrayList<>(n);
        List<Set<Neighbor>> reverseNeighbors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            neighbors.add(new HashSet<>());
            reverseNeighbors.add(new HashSet<>());
        }

        for (int i = 0; i < n; i++) {
            Set<Integer> set = neighbors.get(i);
            PriorityQueue<Neighbor> pq = heap.get(i);
            for (var neighbor : pq) {
                set.add(neighbor.index);
                reverseNeighbors.get(neighbor.index).add(new Neighbor(i, neighbor.distance));
            }
        }

        for (int i = 0; i < n; i++) {
            Set<Integer> set = neighbors.get(i);
            PriorityQueue<Neighbor> pq = heap.get(i);
            for (var neighbor : reverseNeighbors.get(i)) {
                if (!set.contains(neighbor.index)) {
                    if (neighbor.distance < pq.peek().distance) {
                        Neighbor top = pq.poll();
                        top.index = neighbor.index;
                        top.distance = neighbor.distance;
                        pq.offer(top);
                    }
                }
            }
        }
    }

    /** Returns a near neighbor graph with heaps. */
    private static NearestNeighborGraph toGraph(List<PriorityQueue<Neighbor>> heap, int k) {
        int n = heap.size();
        int[][] neighbors = new int[n][k];
        double[][] distances = new double[n][k];
        for (int i = 0; i < n; i++) {
            PriorityQueue<Neighbor> pq = heap.get(i);
            int j = pq.size();
            while (!pq.isEmpty()) {
                Neighbor neighbor = pq.poll();
                if (--j < k) {
                    neighbors[i][j] = neighbor.index;
                    distances[i][j] = neighbor.distance;
                }
            }
        }

        return new NearestNeighborGraph(k, neighbors, distances);
    }

    private interface CandidateGenerator {
        int[] generate(int n, int k, int i);
    }

    /**
     * Creates an approximate nearest neighbor graph with random projection
     * forest and Euclidean distance.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @return approximate k-nearest neighbor graph.
     */
    public static NearestNeighborGraph descent(double[][] data, int k) {
        return descent(data, k, 5, k, 50, 50, 0.001);
    }

    /**
     * Creates an approximate nearest neighbor graph with random projection
     * forest and Euclidean distance.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @param numTrees the number of trees.
     * @param leafSize the maximum size of leaf node.
     * @param maxCandidates the maximum number of candidates in nearest neighbor search.
     * @param maxIter the maximum number of iterations.
     * @param delta Controls the early stop due to limited progress. Larger values
     *              will result in earlier aborts, providing less accurate indexes,
     *              and less accurate searching.
     * @return approximate k-nearest neighbor graph.
     */
    public static NearestNeighborGraph descent(double[][] data, int k, int numTrees, int leafSize,
                                               int maxCandidates, int maxIter, double delta) {
        int n = data.length;
        List<PriorityQueue<Neighbor>> heapList = new ArrayList<>(data.length);
        List<Set<Integer>> neighborSetList = new ArrayList<>(data.length);
        for (int i = 0; i < data.length; i++) {
            heapList.add(new PriorityQueue<>());
            neighborSetList.add(new HashSet<>());
        }

        for (int ti = 0; ti < numTrees; ti++) {
            RandomProjectionTree tree = RandomProjectionTree.of(data, leafSize, false);
            for (int[] leaf : tree.leafSamples()) {
                for (int li = 0; li < leaf.length; li++) {
                    int i = leaf[li];
                    double[] xi = data[i];
                    for (int lj = li + 1; lj < leaf.length; lj++) {
                        int j = leaf[lj];
                        double[] xj = data[j];
                        double dist = MathEx.distance(xi, xj);

                        updateHeap(heapList.get(i), neighborSetList.get(i), k, j, dist);
                        updateHeap(heapList.get(j), neighborSetList.get(j), k, i, dist);
                    }
                }
            }
        }

        return descent(data, MathEx::distance, heapList, k, maxCandidates, maxIter, delta);
    }

    private static boolean updateHeap(PriorityQueue<Neighbor> pq, Set<Integer> set, int k, int index, double dist) {
        if (!set.contains(index)) {
            if (pq.size() < k) {
                pq.add(new Neighbor(index, dist));
                set.add(index);
                return true;
            } else {
                if (dist < pq.peek().distance) {
                    var top = pq.poll();
                    set.remove(top.index);
                    set.add(index);
                    top.distance = dist;
                    top.index = index;
                    pq.offer(top);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates an approximate nearest neighbor graph with the NN-Descent algorithm.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @param distance the distance function.
     * @param <T> the type of data objects.
     * @return approximate k-nearest neighbor graph.
     */
    public static <T> NearestNeighborGraph descent(T[] data, Metric<T> distance, int k) {
        return descent(data, distance, k, 50, 10, 0.001);
    }

    /**
     * Creates an approximate nearest neighbor graph with the NN-Descent algorithm.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @param distance the distance function.
     * @param maxCandidates the maximum number of candidates in nearest neighbor search.
     * @param maxIter the maximum number of iterations.
     * @param delta Controls the early stop due to limited progress. Larger values
     *              will result in earlier aborts, providing less accurate indexes,
     *              and less accurate searching.
     * @param <T> the type of data objects.
     * @return approximate k-nearest neighbor graph.
     */
    public static <T> NearestNeighborGraph descent(T[] data, Metric<T> distance, int k, int maxCandidates, int maxIter, double delta) {
        if (k < 2) {
            throw new IllegalArgumentException("k must be greater than 1: " + k);
        }
        var heap = build(data, distance, k, NearestNeighborGraph::rejectionSample);
        extend(heap);
        return descent(data, distance, heap, k, maxCandidates, maxIter, delta);
    }

    private static <T> NearestNeighborGraph descent(T[] data, Metric<T> distance, List<PriorityQueue<Neighbor>> heapList,
                                                    int k, int maxCandidates, int maxIter, double delta) {
        int n = data.length;
        List<Set<Integer>> neighborSetList = new ArrayList<>(data.length);
        for (int i = 0; i < data.length; i++) {
            neighborSetList.add(new HashSet<>());
        }
        for (int i = 0; i < n; i++) {
            var set = neighborSetList.get(i);
            for (var neighbor : heapList.get(i)) {
                set.add(neighbor.index);
            }
        }

        for (int iter = 1; iter <= maxIter; iter++) {
            int count = 0;
            var candidates = generateCandidates(heapList, maxCandidates);
            for (int i = 0; i < n; i++) {
                for (var j : candidates[i]) {
                    double dist = distance.d(data[i], data[j]);
                    if (updateHeap(heapList.get(i), neighborSetList.get(i), k, j, dist)) {
                        ++count;
                    }

                    if (updateHeap(heapList.get(j), neighborSetList.get(j), k, i, dist)) {
                        ++count;
                    }
                }
            }

            logger.info("NearestNeighborDescent iteration {}: {}", iter, count);
            if (count <= delta * k * n) {
                break;
            }
        }

        return toGraph(heapList, k);
    }

    private static int[][] generateCandidates(List<PriorityQueue<Neighbor>> heapList, int maxCandidates) {
        int n = heapList.size();
        List<Set<Neighbor>> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            candidates.add(new HashSet<>());
        }

        for (int i = 0; i < n; i++) {
            var pqi = heapList.get(i);
            for (var ni : pqi) {
                int j = ni.index;
                double dij = ni.distance;

                var pqj = heapList.get(j);
                for (var nj : pqj) {
                    int k = nj.index;
                    double djk = nj.distance;
                    candidates.get(i).add(new Neighbor(k, dij + djk));
                    candidates.get(k).add(new Neighbor(i, dij + djk));
                }
            }
        }

        int[][] result = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Neighbor> list = new ArrayList<>(candidates.get(i));
            list.sort(Comparator.comparingDouble(o -> o.distance));
            result[i] = list.stream().limit(maxCandidates).mapToInt(neighbor -> neighbor.index).toArray();
        }
        return result;
    }

    /**
     * Generate k integers from 0 to n such that no integer is selected twice.
     * @param n The upper bound of samples.
     * @param k The number of random samples.
     * @param i samples should not equal i.
     * @return random samples.
     */
    private static int[] rejectionSample(int n, int k, int i) {
        if (k > n) {
            throw new IllegalArgumentException();
        }

        int[] samples = new int[k];
        for (int j = 0; j < k; j++) {
            boolean loop = true;
            while (loop) {
                loop = false;
                samples[j] = MathEx.randomInt(n);
                if (samples[j] == i) {
                    loop = true;
                } else {
                    for (int l = 0; l < j; l++) {
                        if (samples[j] == samples[l]) {
                            loop = true;
                            break;
                        }
                    }
                }
            }
        }

        return samples;
    }
}
