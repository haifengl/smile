/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.clustering;

import java.util.Arrays;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;

/**
 * In centroid-based clustering, clusters are represented by a central vector,
 * which may not necessarily be a member of the data set. When the number of
 * clusters is fixed to k, k-means clustering gives a formal definition as
 * an optimization problem: find the k cluster centers and assign the objects
 * to the nearest cluster center, such that the squared distances from the
 * cluster are minimized.
 *
 * Variations of k-means include restricting the centroids to members of
 * the data set (k-medoids), choosing medians (k-medians clustering),
 * choosing the initial centers less randomly (k-means++) or allowing a
 * fuzzy cluster assignment (fuzzy c-means), etc.
 *
 * Most k-means-type algorithms require the number of clusters to be
 * specified in advance, which is considered to be one of the biggest
 * drawbacks of these algorithms. Furthermore, the algorithms prefer
 * clusters of approximately similar size, as they will always assign
 * an object to the nearest centroid. This often leads to incorrectly
 * cut borders of clusters (which is not surprising since the algorithm
 * optimizes cluster centers, not cluster borders).
 *
 * @param <T> the type of centroids.
 * @param <U> the tpe of observations. Usually, T and U are the same.
 *            But in case of SIB, they are different.
 *
 * @author Haifeng Li
 */
public abstract class CentroidClustering<T, U> extends PartitionClustering implements Comparable<CentroidClustering<T, U>> {
    private static final long serialVersionUID = 2L;

    /**
     * The total distortion.
     */
    public final double distortion;
    /**
     * The centroids of each cluster.
     */
    public final T[] centroids;

    /**
     * Constructor.
     * @param distortion the total distortion.
     * @param centroids the centroids of each cluster.
     * @param y the cluster labels.
     */
    public CentroidClustering(double distortion, T[] centroids, int[] y) {
        super(centroids.length, y);
        this.distortion = distortion;
        this.centroids = centroids;
    }

    @Override
    public int compareTo(CentroidClustering<T, U> o) {
        return Double.compare(distortion, o.distortion);
    }

    /** The distance function. */
    public abstract double distance(T x, U y);

    /**
     * Classifies a new observation.
     * @param x a new observation.
     * @return the cluster label.
     */
    public int predict(U x) {
        double nearest = Double.MAX_VALUE;
        int label = 0;

        for (int i = 0; i < k; i++) {
            double dist = distance(centroids[i], x);
            if (dist < nearest) {
                nearest = dist;
                label = i;
            }
        }

        return label;
    }

    @Override
    public String toString() {
        return String.format("Cluster distortion: %.5f%n", distortion) + super.toString();
    }

    /**
     * Assigns each observation to the nearest centroid.
     */
    static <T> double assign(int[] y, T[] data, T[] centroids, ToDoubleBiFunction<T, T> distance) {
        int k = centroids.length;
        double wcss = IntStream.range(0, data.length).parallel().mapToDouble(i -> {
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < k; j++) {
                double dist = distance.applyAsDouble(data[i], centroids[j]);
                if (nearest > dist) {
                    nearest = dist;
                    y[i] = j;
                }
            }
            return nearest;
        }).sum();

        return wcss;
    }

    /**
     * Calculates the new centroids in the new clusters.
     */
    static void updateCentroids(double[][] centroids, double[][] data, int[] y, int[] size) {
        int n = data.length;
        int k = centroids.length;
        int d = centroids[0].length;

        Arrays.fill(size, 0);
        IntStream.range(0, k).parallel().forEach(cluster -> {
            Arrays.fill(centroids[cluster], 0.0);
            for (int i = 0; i < n; i++) {
                if (y[i] == cluster) {
                    size[cluster]++;
                    for (int j = 0; j < d; j++) {
                        centroids[cluster][j] += data[i][j];
                    }
                }
            }

            for (int j = 0; j < d; j++) {
                centroids[cluster][j] /= size[cluster];
            }
        });
    }

    /**
     * Calculates the new centroids in the new clusters with missing values.
     * @param notNaN the number of non-missing values per cluster per variable.
     */
    static void updateCentroidsWithMissingValues(double[][] centroids, double[][] data, int[] y, int[] size, int[][] notNaN) {
        int n = data.length;
        int k = centroids.length;
        int d = centroids[0].length;

        IntStream.range(0, k).parallel().forEach(cluster -> {
            Arrays.fill(centroids[cluster], 0);
            Arrays.fill(notNaN[cluster], 0);
            for (int i = 0; i < n; i++) {
                if (y[i] == cluster) {
                    size[cluster]++;
                    for (int j = 0; j < d; j++) {
                        if (!Double.isNaN(data[i][j])) {
                            centroids[cluster][j] += data[i][j];
                            notNaN[cluster][j]++;
                        }
                    }
                }
            }

            for (int j = 0; j < d; j++) {
                centroids[cluster][j] /= notNaN[cluster][j];
            }
        });
    }
}
