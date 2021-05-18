/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.manifold;

import smile.graph.AdjacencyList;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;

/**
 * Nearest neighbor graph builder.
 *
 * @author Haifeng Li
 */
class NearestNeighborGraph {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NearestNeighborGraph.class);

    /**
     * The original sample index.
     */
    public final int[] index;
    /**
     * Nearest neighbor graph.
     */
    public final AdjacencyList graph;

    /**
     * Constructor.
     * @param index the original sample index.
     * @param graph the nearest neighbor graph.
     */
    public NearestNeighborGraph(int[] index, AdjacencyList graph) {
        this.index = index;
        this.graph = graph;
    }

    /** The nearest neighbor edge consumer. */
    public interface EdgeConsumer {
        /**
         * Performs this operation on the edge.
         * @param v1 the center of neighborhood.
         * @param v2 the neighbor.
         * @param weight the weight/distance.
         * @param j the index of neighbor of knn search.
         */
        void accept(int v1, int v2, double weight, int j);
    }

    /**
     * Creates a nearest neighbor graph with Euclidean distance.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @param digraph flag to create a directed graph.
     * @param consumer an optional lambda to perform some side effect operations.
     */
    public static AdjacencyList of(double[][] data, int k, boolean digraph, EdgeConsumer consumer) {
        return of(data, new EuclideanDistance(), k, digraph ,consumer);
    }

    /**
     * Creates a nearest neighbor graph.
     *
     * @param data the dataset.
     * @param k k-nearest neighbor.
     * @param distance the distance function.
     * @param digraph flag to create a directed graph.
     * @param consumer an optional lambda to perform some side effect operations.
     */
    public static <T> AdjacencyList of(T[] data, Distance<T> distance, int k, boolean digraph, EdgeConsumer consumer) {
        // This is actually faster on many core systems.
        LinearSearch<T> knn = new LinearSearch<>(data, distance);

        int n = data.length;
        AdjacencyList graph = new AdjacencyList(n, digraph);

        if (consumer != null) {
            for (int i = 0; i < n; i++) {
                Neighbor<T, T>[] neighbors = knn.knn(data[i], k);

                int v1 = i;
                for (int j = 0; j < neighbors.length; j++) {
                    int v2 = neighbors[j].index;
                    double weight = neighbors[j].distance;
                    graph.setWeight(v1, v2, weight);
                    consumer.accept(v1, v2, weight, j);
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                for (Neighbor<T, T> neighbor : knn.knn(data[i], k)) {
                    graph.setWeight(i, neighbor.index, neighbor.distance);
                }
            }
        }

        return graph;
    }

    /**
     * Finds the largest connected components of a nearest neighbor graph.
     * If the graph has multiple connected components, keep the
     * largest one.
     *
     * @param graph the nearest neighbor graph.
     */
    public static NearestNeighborGraph largest(AdjacencyList graph) {
        int n = graph.getNumVertices();

        // Use largest connected component.
        int[][] cc = graph.bfs();
        int[] index;
        if (cc.length == 1) {
            index = new int[n];
            for (int i = 0; i < n; i++) {
                index[i] = i;
            }
        } else {
            n = 0;
            int largest = 0;
            for (int i = 0; i < cc.length; i++) {
                if (cc[i].length > n) {
                    largest = i;
                    n = cc[i].length;
                }
            }

            logger.info("{} connected components, largest one has {} samples.", cc.length, n);

            index = cc[largest];
            graph = graph.subgraph(index);
        }

        return new NearestNeighborGraph(index, graph);
    }
}
