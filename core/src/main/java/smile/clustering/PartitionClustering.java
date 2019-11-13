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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.MathEx;

/**
 * Partition clustering. Partition methods classify the observations
 * into distinct non-overlapping groups.
 * 
 * @author Haifeng Li
 */
public abstract class PartitionClustering implements Serializable {
    /**
     * Cluster label for outliers or noises.
     */
    public static final int OUTLIER = Integer.MAX_VALUE;

    /**
     * The number of clusters.
     */
    public final int k;
    /**
     * The cluster labels of data.
     */
    public final int[] y;
    /**
     * The number of observations in each cluster.
     */
    public final int[] size;

    /**
     * Constructor.
     * @param k the number of clusters.
     * @param y the cluster labels.
     */
    public PartitionClustering(int k, int[] y) {
        this.k = k;
        this.y = y;

        this.size = new int[k + 1];
        for (int yi : y) {
            if (yi == OUTLIER) {
                size[k]++;
            } else {
                size[yi]++;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Cluster size of %d data points:%n", y.length));
        for (int i = 0; i < k; i++) {
            double r = 100.0 * size[i] / y.length;
            sb.append(String.format("Cluster %4d %6d (%4.1f%%)%n", i+1, size[i], r));
        }

        if (size[k] != 0) {
            double r = 100.0 * size[k] / y.length;
            sb.append(String.format("Outliers     %6d (%4.1f%%)%n", size[k], r));
        }

        return sb.toString();
    }

    /**
     * Initialize cluster membership of input objects with K-Means++ algorithm.
     * Many clustering methods, e.g. k-means, need a initial clustering
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
     * <h2>References</h2>
     * <ol>
     * <li> D. Arthur and S. Vassilvitskii. "K-means++: the advantages of careful seeding". ACM-SIAM symposium on Discrete algorithms, 1027-1035, 2007.</li>
     * <li> Anna D. Peterson, Arka P. Ghosh and Ranjan Maitra. A systematic evaluation of different methods for initializing the K-means clustering algorithm. 2010.</li>
     * </ol>
     * 
     * @param <T> the type of input object.
     * @param data data objects array of size n.
     * @param medoids an array of size k to store cluster medoids on output.
     * @param y an array of size n to store cluster labels on output.
     * @return an array of size n to store the distance of each observation to nearest medoid.
     */
    public static <T> double[] seed(T[] data, T[] medoids, int[] y, ToDoubleBiFunction<T, T> distance) {
        int n = data.length;
        int k = medoids.length;
        double[] d = new double[n];
        medoids[0] = data[MathEx.randomInt(n)];

        Arrays.fill(d, Double.MAX_VALUE);

        // pick the next center
        for (int j = 1; j <= k; j++) {
            final int prev = j - 1;
            final T medoid = medoids[prev];
            // Loop over the observations and compare them to the most recent center.  Store
            // the distance from each observation to its closest center in scores.
            IntStream.range(0, n).parallel().forEach(i -> {
                // compute the distance between this observation and the current center
                double dist = distance.applyAsDouble(data[i], medoid);
                if (dist < d[i]) {
                    d[i] = dist;
                    y[i] = prev;
                }
            });

            if (j < k) {
                double cost = 0.0;
                double cutoff = MathEx.random() * MathEx.sum(d);
                for (int index = 0; index < n; index++) {
                    cost += d[index];
                    if (cost >= cutoff) {
                        medoids[j] = data[index];
                        break;
                    }
                }
            }
        }

        return d;
    }

    /**
     * Runs a clustering algorithm multiple times and return the best one
     * (e.g. smallest distortion).
     * @param runs the number of runs.
     */
    public static <T extends PartitionClustering & Comparable<? super T>> T run(int runs, Supplier<T> clustering) {
        if (runs <= 0) {
            throw new IllegalArgumentException("Invalid number of runs: " + runs);
        }

        return IntStream.range(0, runs)
                .mapToObj(run -> clustering.get())
                .min(Comparator.naturalOrder())
                .get();
    }
}
