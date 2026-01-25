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
package smile.clustering;

import java.io.Serial;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import smile.neighbor.Neighbor;
import smile.neighbor.KDTree;
import smile.neighbor.LinearSearch;
import smile.neighbor.RNNSearch;
import smile.math.distance.Distance;
import static smile.clustering.Clustering.OUTLIER;

/**
 * Density-Based Spatial Clustering of Applications with Noise.
 * DBSCAN finds a number of clusters starting from the estimated density
 * distribution of corresponding nodes.
 * <p>
 * DBSCAN requires two parameters: radius (i.e. neighborhood radius) and the
 * number of minimum points required to form a cluster (minPts). It starts
 * with an arbitrary starting point that has not been visited. This point's
 * neighborhood is retrieved, and if it contains sufficient number of points,
 * a cluster is started. Otherwise, the point is labeled as noise. Note that
 * this point might later be found in a sufficiently sized radius-environment
 * of a different point and hence be made part of a cluster.
 * <p>
 * If a point is found to be part of a cluster, its neighborhood is also
 * part of that cluster. Hence, all points that are found within the
 * neighborhood are added, as is their own neighborhood. This process
 * continues until the cluster is completely found. Then, a new unvisited point
 * is retrieved and processed, leading to the discovery of a further cluster
 * of noise.
 * <p>
 * DBSCAN visits each point of the database, possibly multiple times (e.g.,
 * as candidates to different clusters). For practical considerations, however,
 * the time complexity is mostly governed by the number of nearest neighbor
 * queries. DBSCAN executes exactly one such query for each point, and if
 * an indexing structure is used that executes such a neighborhood query
 * in O(log n), an overall runtime complexity of O(n log n) is obtained.
 * <p>
 * DBSCAN has many advantages such as
 * <ul>
 * <li> DBSCAN does not need to know the number of clusters in the data
 *      a priori, as opposed to k-means.
 * <li> DBSCAN can find arbitrarily shaped clusters. It can even find clusters
 *      completely surrounded by (but not connected to) a different cluster.
 *      Due to the MinPts parameter, the so-called single-link effect
 *     (different clusters being connected by a thin line of points) is reduced.
 * <li> DBSCAN has a notion of noise. Outliers are labeled as PartitionClustering.OUTLIER,
 *      which is Integer.MAX_VALUE.
 * <li> DBSCAN requires just two parameters and is mostly insensitive to the
 *      ordering of the points in the database. (Only points sitting on the
 *      edge of two different clusters might swap cluster membership if the
 *      ordering of the points is changed, and the cluster assignment is unique
 *      only up to isomorphism.)
 * </ul>
 * On the other hand, DBSCAN has the disadvantages of
 * <ul>
 * <li> In high dimensional space, the data are sparse everywhere
 *      because of the curse of dimensionality. Therefore, DBSCAN doesn't
 *      work well on high-dimensional data in general.
 * <li> DBSCAN does not respond well to data sets with varying densities.
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li> Martin Ester, Hans-Peter Kriegel, Jorg Sander, Xiaowei Xu (1996-). A density-based algorithm for discovering clusters in large spatial databases with noise". KDD, 1996. </li>
 * <li> Jorg Sander, Martin Ester, Hans-Peter  Kriegel, Xiaowei Xu. (1998). Density-Based Clustering in Spatial Databases: The Algorithm GDBSCAN and Its Applications. 1998. </li>
 * </ol>
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public class DBSCAN<T> extends Partitioning {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The minimum number of points required to form a cluster
     */
    private final int minPoints;
    /**
     * The neighborhood radius.
     */
    private final double radius;
    /**
     * Data structure for neighborhood search.
     */
    private final RNNSearch<T,T> nns;
    /**
     * The flag if the point is a core point (at least minPts points are within radius).
     */
    private final boolean[] core;

    /**
     * Constructor.
     * @param k the number of clusters.
     * @param group the cluster labels.
     * @param core the flag if the point is a core point.
     * @param minPoints the minimum number of neighbors for a core data point.
     * @param radius the neighborhood radius.
     * @param nns the data structure for neighborhood search.
     */
    public DBSCAN(int k, int[] group, boolean[] core, int minPoints, double radius, RNNSearch<T,T> nns) {
        super(k, group);
        this.minPoints = minPoints;
        this.radius = radius;
        this.nns = nns;
        this.core = core;
    }

    /**
     * Returns the minimum number of neighbors for a core data point.
     * @return the minimum number of neighbors for a core data point.
     */
    public int minPoints() {
        return minPoints;
    }

    /**
     * Returns the neighborhood radius.
     * @return the neighborhood radius.
     */
    public double radius() {
        return radius;
    }

    /**
     * Clustering the data with KD-tree. DBSCAN is generally applied on
     * low-dimensional data. Therefore, KD-tree can speed up the nearest
     * neighbor search a lot.
     * @param data the observations.
     * @param minPts the minimum number of neighbors for a core data point.
     * @param radius the neighborhood radius.
     * @return the model.
     */
    public static DBSCAN<double[]> fit(double[][] data, int minPts, double radius) {
        return fit(data, new KDTree<>(data, data), minPts, radius);
    }

    /**
     * Clustering the data.
     * @param data the observations.
     * @param distance the distance function.
     * @param minPts the minimum number of neighbors for a core data point.
     * @param radius the neighborhood radius.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> DBSCAN<T> fit(T[] data, Distance<T> distance, int minPts, double radius) {
        return fit(data, LinearSearch.of(data, distance), minPts, radius);
    }

    /**
     * Clustering the data.
     * @param data the observations.
     * @param nns the data structure for neighborhood search.
     * @param minPts the minimum number of neighbors for a core data point.
     * @param radius the neighborhood radius.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> DBSCAN<T> fit(T[] data, RNNSearch<T,T> nns, int minPts, double radius) {
        if (minPts < 1) {
            throw new IllegalArgumentException("Invalid minPts: " + minPts);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        // The label for data samples in BFS queue.
        final int QUEUED = -2;
        // The label for unclassified data samples.
        final int UNDEFINED = -1;

        int k = 0;
        int n = data.length;
        boolean[] core = new boolean[n];
        int[] group = new int[n];
        Arrays.fill(group, UNDEFINED);

        for (int i = 0; i < data.length; i++) {
            if (group[i] == UNDEFINED) {
                List<Neighbor<T,T>> neighbors = new ArrayList<>();
                nns.search(data[i], radius, neighbors);
                if (neighbors.size() < minPts) {
                    group[i] = OUTLIER;
                } else {
                    group[i] = k;
                    core[i] = true;

                    for (Neighbor<T, T> neighbor : neighbors) {
                        if (group[neighbor.index()] == UNDEFINED) {
                            group[neighbor.index()] = QUEUED;
                        }
                    }

                    for (int j = 0; j < neighbors.size(); j++) {
                        Neighbor<T,T> neighbor = neighbors.get(j);
                        int index = neighbor.index();

                        if (group[index] == OUTLIER) {
                            group[index] = k;
                        }

                        if (group[index] == UNDEFINED || group[index] == QUEUED) {
                            group[index] = k;

                            List<Neighbor<T,T>> secondaryNeighbors = new ArrayList<>();
                            nns.search(neighbor.key(), radius, secondaryNeighbors);

                            if (secondaryNeighbors.size() >= minPts) {
                                core[neighbor.index()] = true;
                                for (var secondaryNeighbor : secondaryNeighbors) {
                                    int label = group[secondaryNeighbor.index()];
                                    if (label == UNDEFINED) {
                                        group[secondaryNeighbor.index()] = QUEUED;
                                    }

                                    if (label == UNDEFINED || label == OUTLIER) {
                                        neighbors.add(secondaryNeighbor);
                                    }
                                }
                            }
                        }
                    }

                    k++;
                }
            }
        }

        return new DBSCAN<>(k, group, core, minPts, radius, nns);
    }

    /**
     * Classifies a new observation.
     * @param x a new observation.
     * @return the cluster label. Note that it may be {@link Clustering#OUTLIER}.
     */
    public int predict(T x) {
        List<Neighbor<T,T>> neighbors = new ArrayList<>();
        nns.search(x, radius, neighbors);

        Collections.sort(neighbors);
        for (Neighbor<T, T> neighbor : neighbors) {
            if (core[neighbor.index()]) {
                return group[neighbor.index()];
            }
        }

        return OUTLIER;
    }
}
