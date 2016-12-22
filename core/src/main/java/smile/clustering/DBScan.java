/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.clustering;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import smile.neighbor.Neighbor;
import smile.neighbor.RNNSearch;
import smile.neighbor.LinearSearch;
import smile.neighbor.CoverTree;
import smile.math.Math;
import smile.math.distance.Distance;
import smile.math.distance.Metric;

/**
 * Density-Based Spatial Clustering of Applications with Noise.
 * DBScan finds a number of clusters starting from the estimated density
 * distribution of corresponding nodes.
 * <p>
 * DBScan requires two parameters: radius (i.e. neighborhood radius) and the
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
 * DBScan visits each point of the database, possibly multiple times (e.g.,
 * as candidates to different clusters). For practical considerations, however,
 * the time complexity is mostly governed by the number of nearest neighbor
 * queries. DBScan executes exactly one such query for each point, and if
 * an indexing structure is used that executes such a neighborhood query
 * in O(log n), an overall runtime complexity of O(n log n) is obtained.
 * <p>
 * DBScan has many advantages such as
 * <ul>
 * <li> DBScan does not need to know the number of clusters in the data
 *      a priori, as opposed to k-means.
 * <li> DBScan can find arbitrarily shaped clusters. It can even find clusters
 *      completely surrounded by (but not connected to) a different cluster.
 *      Due to the MinPts parameter, the so-called single-link effect
 *     (different clusters being connected by a thin line of points) is reduced.
 * <li> DBScan has a notion of noise. Outliers are labeled as Clustering.OUTLIER,
 *      which is Integer.MAX_VALUE.
 * <li> DBScan requires just two parameters and is mostly insensitive to the
 *      ordering of the points in the database. (Only points sitting on the
 *      edge of two different clusters might swap cluster membership if the
 *      ordering of the points is changed, and the cluster assignment is unique
 *      only up to isomorphism.)
 * </ul>
 * On the other hand, DBScan has the disadvantages of
 * <ul>
 * <li> In high dimensional space, the data are sparse everywhere
 *      because of the curse of dimensionality. Therefore, DBScan doesn't
 *      work well on high-dimensional data in general.
 * <li> DBScan does not respond well to data sets with varying densities.
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
public class DBScan <T> extends PartitionClustering<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Label for unclassified data samples.
     */
    private static final int UNCLASSIFIED = -1;
    /**
     * The minimum number of points required to form a cluster
     */
    private double minPts;
    /**
     * The range of neighborhood.
     */
    private double radius;
    /**
     * Data structure for neighborhood search.
     */
    private RNNSearch<T,T> nns;

    /**
     * Constructor. Clustering the data. Note that this one could be very
     * slow because of brute force nearest neighbor search.
     * @param data the dataset for clustering.
     * @param distance the distance measure for neighborhood search.
     * @param minPts the minimum number of neighbors for a core data point.
     * @param radius the neighborhood radius.
     */
    public DBScan(T[] data, Distance<T> distance, int minPts, double radius) {
        this(data, new LinearSearch<>(data, distance), minPts, radius);
    }

    /**
     * Constructor. Clustering the data. Using cover tree for nearest neighbor
     * search.
     * @param data the dataset for clustering.
     * @param distance the distance measure for neighborhood search.
     * @param minPts the minimum number of neighbors for a core data point.
     * @param radius the neighborhood radius.
     */
    public DBScan(T[] data, Metric<T> distance, int minPts, double radius) {
        this(data, new CoverTree<>(data, distance), minPts, radius);
    }

    /**
     * Clustering the data.
     * @param data the dataset for clustering.
     * @param nns the data structure for neighborhood search.
     * @param minPts the minimum number of neighbors for a core data point.
     * @param radius the neighborhood radius.
     */
    public DBScan(T[] data, RNNSearch<T,T> nns, int minPts, double radius) {
        if (minPts < 1) {
            throw new IllegalArgumentException("Invalid minPts: " + minPts);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        this.nns = nns;
        this.minPts = minPts;
        this.radius = radius;
        
        k = 0;

        int n = data.length;
        y = new int[n];
        Arrays.fill(y, UNCLASSIFIED);

        for (int i = 0; i < data.length; i++) {
            if (y[i] == UNCLASSIFIED) {
                List<Neighbor<T,T>> neighbors = new ArrayList<>();
                nns.range(data[i], radius, neighbors);
                if (neighbors.size() < minPts) {
                    y[i] = OUTLIER;
                } else {
                    y[i] = k;
                    for (int j = 0; j < neighbors.size(); j++) {
                        if (y[neighbors.get(j).index] == UNCLASSIFIED) {
                            y[neighbors.get(j).index] = k;
                            Neighbor<T,T> neighbor = neighbors.get(j);
                            List<Neighbor<T,T>> secondaryNeighbors = new ArrayList<>();
                            nns.range(neighbor.key, radius, secondaryNeighbors);

                            if (secondaryNeighbors.size() >= minPts) {
                                neighbors.addAll(secondaryNeighbors);
                            }
                        }

                        if (y[neighbors.get(j).index] == OUTLIER) {
                            y[neighbors.get(j).index] = k;
                        }
                    }
                    k++;
                }
            }
        }

        size = new int[k + 1];
        for (int i = 0; i < n; i++) {
            if (y[i] == OUTLIER) {
                size[k]++;
            } else {
                size[y[i]]++;
            }
        }
    }
    
    /**
     * Returns the parameter of minimum number of neighbors.
     */
    public double getMinPts() {
        return minPts;
    }

    /**
     * Returns the radius of neighborhood.
     */
    public double getRadius() {
        return radius;
    }

    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label. Note that it may be {@link #OUTLIER}.
     */
    @Override
    public int predict(T x) {
        List<Neighbor<T,T>> neighbors = new ArrayList<>();
        nns.range(x, radius, neighbors);
        
        if (neighbors.size() < minPts) {
            return OUTLIER;
        }
        
        int[] label = new int[k + 1];
        for (Neighbor<T,T> neighbor : neighbors) {
            int yi = y[neighbor.index];
            if (yi == OUTLIER) yi = k;
            label[yi]++;
        }
        
        int c = Math.whichMax(label);
        if (c == k) c = OUTLIER;
        return c;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("DBScan clusters of %d data points:%n", y.length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }

        int r = (int) Math.round(1000.0 * size[k] / y.length);
        sb.append(String.format("Noise\t%5d (%2d.%1d%%)%n", size[k], r / 10, r % 10));
        
        return sb.toString();
    }
}
