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
package smile.clustering;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.MathEx;

/**
 * Centroid-based clustering that uses the center of each cluster to group
 * similar data points into clusters. The cluster centers may not necessarily
 * be a member of the data set. When the number of clusters is fixed to k,
 * k-means clustering gives a formal definition as an optimization problem:
 * find the k cluster centers and assign the objects to the nearest cluster
 * center, such that the squared distances from the cluster are minimized.
 * <p>
 * Variations of k-means include restricting the centroids to members of
 * the data set (k-medoids), choosing medians (k-medians clustering),
 * choosing the initial centers less randomly (k-means++) or allowing a
 * fuzzy cluster assignment (fuzzy c-means), etc.
 * <p>
 * Most k-means-type algorithms require the number of clusters to be
 * specified in advance, which is considered to be one of the biggest
 * drawbacks of these algorithms. Furthermore, the algorithms prefer
 * clusters of approximately similar size, as they will always assign
 * an object to the nearest centroid. This often leads to incorrectly
 * cut borders of clusters (which is not surprising since the algorithm
 * optimizes cluster centers, not cluster borders).
 *
 * @param centers The cluster centroids or medoids.
 * @param distance The distance function.
 * @param group The cluster labels of data.
 * @param proximity The squared distance between data points and their
 *                  respective cluster centers.
 * @param size The number of data points in each cluster.
 * @param distortions The average squared distance of data points within each cluster.
 * @param <T> the type of centroids.
 * @param <U> the type of observations. Usually, T and U are the same.
 *            But in case of SIB, they are different.
 * @author Haifeng Li
 */
public record CentroidClustering<T, U>(T[] centers, ToDoubleBiFunction<T, U> distance,
                                       int[] group, double[] proximity, int[] size,
                                       double[] distortions)
        implements Comparable<CentroidClustering<T, U>>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     * @param centers The cluster centroids or medoids.
     * @param distance The distance function.
     * @param group The cluster labels of data.
     * @param proximity The squared distance of each data point to its nearest cluster center.
     */
    public CentroidClustering(T[] centers, ToDoubleBiFunction<T, U> distance, int[] group, double[] proximity) {
        this(centers, distance, group, proximity, new int[centers.length+1], new double[centers.length+1]);

        int k = centers.length;
        distortions[k] = 0;
        for (int i = 0; i < group.length; i++) {
            int y = group[i];
            if (y == Clustering.OUTLIER) {
                size[k]++;
            } else {
                size[y]++;
                distortions[y] += proximity[i];
                distortions[k] += proximity[i];
            }
        }

        distortions[k] /= (group.length - size[k]);
        for (int i = 0; i < k; i++) {
            distortions[i] /= size[i];
        }
    }

    /**
     * Returns the number of clusters.
     * @return the number of clusters.
     */
    public int k() {
        return centers.length;
    }

    /**
     * Returns the average squared distance between data points and their
     * respective cluster centers. This is also known as the within-cluster
     * sum-of-squares (WCSS).
     * @return the distortion.
     */
    public double distortion() {
        return distortions[centers.length];
    }

    @Override
    public int compareTo(CentroidClustering<T, U> o) {
        return Double.compare(distortion(), o.distortion());
    }

    @Override
    public String toString() {
        int k = centers.length;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Cluster     Size        Distortion%n"));
        for (int i = 0; i < k; i++) {
            double percent = 100.0 * size[i] / group.length;
            sb.append(String.format("Cluster %-4d %6d (%4.1f%%) %8.2f%n", i+1, size[i], percent, distortions[i]));
        }

        if (size[k] != 0) {
            double percent = 100.0 * size[k] / group.length;
            sb.append(String.format("Outliers     %6d (%4.1f%%)%n", size[k], percent));
        }

        sb.append(String.format("Total        %6d (%4.1f%%) %8.2f%n", group.length, 1.0, distortion()));
        return sb.toString();
    }

    /**
     * Returns the center of i-th cluster.
     * @param i the index of cluster.
     * @return the cluster center.
     */
    public T center(int i) {
        return centers[i];
    }

    /**
     * Returns the cluster label of i-th data point.
     * @param i the index of data point.
     * @return the cluster label.
     */
    public int group(int i) {
        return group[i];
    }

    /**
     * Returns the distance of i-th data point to its cluster center.
     * @param i the index of data point.
     * @return the distance to cluster center.
     */
    public double proximity(int i) {
        return proximity[i];
    }

    /**
     * Returns the size of i-th cluster.
     * @param i the index of cluster.
     * @return the cluster size.
     */
    public int size(int i) {
        return size[i];
    }

    /**
     * Returns the radius of i-th cluster.
     * @param i the index of cluster.
     * @return the cluster radius.
     */
    public double radius(int i) {
        return size[i];
    }

    /**
     * Classifies a new observation.
     * @param x a new observation.
     * @return the cluster label.
     */
    public int predict(U x) {
        int label = 0;
        double nearest = Double.MAX_VALUE;

        for (int i = 0; i < centers.length; i++) {
            double dist = distance.applyAsDouble(centers[i], x);
            if (dist < nearest) {
                nearest = dist;
                label = i;
            }
        }

        return label;
    }

    /**
     * Assigns each data point to the nearest centroid.
     * @param data the data points.
     * @return the updated clustering.
     */
    CentroidClustering<T, U> assign(U[] data) {
        int k = centers.length;
        Arrays.fill(size, 0);
        Arrays.fill(distortions, 0);
        double distortion = IntStream.range(0, data.length).parallel().mapToDouble(i -> {
            int cluster = -1;
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < k; j++) {
                double dist = distance.applyAsDouble(centers[j], data[i]);
                if (nearest > dist) {
                    nearest = dist;
                    cluster = j;
                }
            }
            double squared = nearest * nearest;
            proximity[i] = squared;
            group[i] = cluster;
            size[cluster]++;
            distortions[cluster] += squared;
            return squared;
        }).sum();

        for (int i = 0; i < k; i++) {
            distortions[i] /= size[i];
        }
        distortions[k] = MathEx.mean(proximity);
        return new CentroidClustering<>(centers, distance, group, proximity, size, distortions);
    }

    /**
     * Returns a random clustering based on K-Means++ algorithm.
     * Many clustering methods, e.g. k-means, need an initial clustering
     * configuration as a seed.
     * <p>
     * K-Means++ is based on the intuition of spreading the k initial cluster
     * centers away from each other. The first cluster center is chosen uniformly
     * at random from the data points that are being clustered, after which each
     * subsequent cluster center is chosen from the remaining data points with
     * probability proportional to its distance squared to the point's closest
     * cluster center.
     * <p>
     * The exact algorithm is as follows:
     * <ol>
     * <li> Choose one center uniformly at random from among the data points. </li>
     * <li> For each data point x, compute D(x), the distance between x and the nearest center that has already been chosen. </li>
     * <li> Choose one new data point at random as a new center, using a weighted probability distribution where a point x is chosen with probability proportional to D<sup>2</sup>(x). </li>
     * <li> Repeat Steps 2 and 3 until k centers have been chosen. </li>
     * <li> Now that the initial centers have been chosen, proceed using standard k-means clustering. </li>
     * </ol>
     * This seeding method gives out considerable improvements in the final error
     * of k-means. Although the initial selection in the algorithm takes extra time,
     * the k-means part itself converges very fast after this seeding and thus
     * the algorithm actually lowers the computation time too.
     *
     * <ol>
     * <li> D. Arthur and S. Vassilvitskii. "K-means++: the advantages of careful seeding". ACM-SIAM symposium on Discrete algorithms, 1027-1035, 2007.</li>
     * <li> Anna D. Peterson, Arka P. Ghosh and Ranjan Maitra. A systematic evaluation of different methods for initializing the K-means clustering algorithm. 2010.</li>
     * </ol>
     *
     * @param data data objects array of size n.
     * @param medoids an array of size k to store cluster medoids on output.
     * @param distance the distance function.
     * @param <T> the type of input object.
     * @return the initial clustering.
     */
    public static <T> CentroidClustering<T, T> init(T[] data, T[] medoids, ToDoubleBiFunction<T, T> distance) {
        int n = data.length;
        int k = medoids.length;
        int[] group = new int[n];
        double[] proximity = new double[n];
        double[] probability = new double[n];
        Arrays.fill(proximity, Double.MAX_VALUE);
        medoids[0] = data[MathEx.randomInt(n)];

        // pick the next center
        for (int j = 1; j <= k; j++) {
            final int prev = j - 1;
            final T medoid = medoids[prev];
            // Loop over the observations and compare them to the most recent center.  Store
            // the distance from each observation to its closest center in scores.
            IntStream.range(0, n).parallel().forEach(i -> {
                // compute the distance between this observation and the current center
                double dist = distance.applyAsDouble(data[i], medoid);
                if (dist < proximity[i]) {
                    proximity[i] = dist;
                    group[i] = prev;
                }
            });

            if (j < k) {
                for (int i = 0; i < n; i++) {
                    proximity[i] = proximity[i] * proximity[i];
                }
                MathEx.unitize1(probability);
                T center = data[MathEx.random(probability)];
                while (contains(center, medoids, j)) {
                    center = data[MathEx.random(probability)];
                }
                medoids[j] = center;
            }
        }

        return new CentroidClustering<>(medoids, distance, group, proximity);
    }

    /**
     * Selects random samples as seeds for various algorithms.
     * @param data samples to select seeds from.
     * @param k the number of seeds.
     * @return the seeds.
     */
    public static double[][] seeds(double[][] data, int k) {
        double[][] medoids = new double[k][];
        init(data, medoids, MathEx::distance);
        // Make a copy so that further processing won't modify samples.
        double[][] neurons = new double[k][];
        for (int i = 0; i < k; i++) {
            neurons[i] = medoids[i].clone();
        }
        return neurons;
    }

    /**
     * Returns true if the array contains the object.
     */
    static <T> boolean contains(T medoid, T[] medoids) {
        return contains(medoid, medoids, medoids.length);
    }

    /**
     * Returns true if the array contains the object.
     */
    static <T> boolean contains(T medoid, T[] medoids, int length) {
        for (int i = 0; i < length; i++) {
            if (medoids[i] == medoid) return true;
        }
        return false;
    }
}
