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

import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.distance.Distance;

/**
 * Clustering Large Applications based upon RANdomized Search. CLARANS is an
 * efficient medoid-based clustering algorithm. The k-medoids algorithm is an
 * adaptation of the k-means algorithm. Rather than calculate the mean of the
 * items in each cluster, a representative item, or medoid, is chosen for each
 * cluster at each iteration. In CLARANS, the process of finding k medoids from
 * n objects is viewed abstractly as searching through a certain graph. In the
 * graph, a node is represented by a set of k objects as selected medoids. Two
 * nodes are neighbors if their sets differ by only one object. In each iteration,
 * CLARANS considers a set of randomly chosen neighbor nodes as candidate
 * of new medoids. We will move to the neighbor node if the neighbor
 * is a better choice for medoids. Otherwise, a local optima is discovered. The
 * entire process is repeated multiple time to find better.
 * <p>
 * CLARANS has two parameters: the maximum number of neighbors examined
 * (maxNeighbor) and the number of local minima obtained (numLocal). The
 * higher the value of maxNeighbor, the closer is CLARANS to PAM, and the
 * longer is each search of a local minima. But the quality of such a local
 * minima is higher and fewer local minima needs to be obtained.
 *
 * <h2>References</h2>
 * <ol>
 * <li>R. Ng and J. Han. CLARANS: A Method for Clustering Objects for Spatial Data Mining. IEEE TRANS. KNOWLEDGE AND DATA ENGINEERING, 2002.</li>
 * </ol>
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public class CLARANS<T> extends CentroidClustering<T, T> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CLARANS.class);
    /**
     * The lambda of distance measure.
     */
    private final Distance<T> distance;

    /**
     * Constructor.
     *
     * @param distortion the total distortion.
     * @param medoids    the medoids of each cluster.
     * @param y          the cluster labels.
     * @param distance   the lambda of distance measure.
     */
    public CLARANS(double distortion, T[] medoids, int[] y, Distance<T> distance) {
        super(distortion, medoids, y);
        this.distance = distance;
    }

    @Override
    public double distance(T x, T y) {
        return distance.d(x, y);
    }

    /**
     * Clustering data into k clusters. The maximum number of
     * random search is set to 1.25% * k * (n - k), where n is the number of
     * data and k is the number clusters.
     *
     * @param data     the observations.
     * @param k        the number of clusters.
     * @param distance the lambda of distance measure.
     */
    public static <T> CLARANS<T> fit(T[] data, Distance<T> distance, int k) {
        return fit(data, distance, k, (int) Math.round(0.0125 * k * (data.length - k)));
    }

    /**
     * Constructor. Clustering data into k clusters.
     *
     * @param data        the observations.
     * @param k           the number of clusters.
     * @param maxNeighbor the maximum number of neighbors examined during
     *                    the random search of local minima.
     * @param distance    the lambda of distance measure.
     */
    public static <T> CLARANS<T> fit(T[] data, Distance<T> distance, int k, int maxNeighbor) {
        if (maxNeighbor <= 0) {
            throw new IllegalArgumentException("Invalid maxNeighbors: " + maxNeighbor);
        }

        int n = data.length;

        if (k >= n) {
            throw new IllegalArgumentException("Too large k: " + k);
        }

        if (maxNeighbor > n) {
            throw new IllegalArgumentException("Too large maxNeighbor: " + maxNeighbor);
        }

        int minmax = 100;
        if (k * (n - k) < minmax) {
            minmax = k * (n - k);
        }

        if (maxNeighbor < minmax) {
            maxNeighbor = minmax;
        }

        @SuppressWarnings("unchecked")
        T[] medoids = (T[]) java.lang.reflect.Array.newInstance(data.getClass().getComponentType(), k);
        T[] newMedoids = medoids.clone();
        int[] y = new int[n];
        int[] newY = new int[n];
        double[] newD = new double[n];

        double[] d = seed(data, medoids, y, distance);
        double distortion = MathEx.sum(d);

        System.arraycopy(medoids, 0, newMedoids, 0, k);
        System.arraycopy(y, 0, newY, 0, n);
        System.arraycopy(d, 0, newD, 0, n);

        for (int neighborCount = 1; neighborCount <= maxNeighbor; neighborCount++) {
            double randomNeighborDistortion = getRandomNeighbor(data, newMedoids, newY, newD, distance);
            if (randomNeighborDistortion < distortion) {
                logger.info(String.format("Distortion reduces to %.4f after %3d random neighbors", distortion, neighborCount));
                neighborCount = 0;
                distortion = randomNeighborDistortion;
                System.arraycopy(newMedoids, 0, medoids, 0, k);
                System.arraycopy(newY, 0, y, 0, n);
                System.arraycopy(newD, 0, d, 0, n);
            } else {
                System.arraycopy(medoids, 0, newMedoids, 0, k);
                System.arraycopy(y, 0, newY, 0, n);
                System.arraycopy(d, 0, newD, 0, n);
            }
        }

        logger.info(String.format("Final distortion: %.4f", distortion));

        return new CLARANS<>(distortion, medoids, y, distance);
    }

    /**
     * Picks a random neighbor which differs in only one medoid with current clusters.
     */
    private static <T> double getRandomNeighbor(T[] data, T[] medoids, int[] y, double[] d, ToDoubleBiFunction<T, T> distance) {
        int n = data.length;
        int k = medoids.length;

        int cluster = MathEx.randomInt(k);
        T medoid = getRandomMedoid(data, medoids);
        medoids[cluster] = medoid;

        IntStream.range(0, n).parallel().forEach(i -> {
            double dist = distance.applyAsDouble(data[i], medoid);
            if (d[i] > dist) {
                y[i] = cluster;
                d[i] = dist;
            } else if (y[i] == cluster) {
                d[i] = dist;
                for (int j = 0; j < k; j++) {
                    if (j != cluster) {
                        dist = distance.applyAsDouble(data[i], medoids[j]);
                        if (d[i] > dist) {
                            d[i] = dist;
                            y[i] = j;
                        }
                    }
                }
            }
        });

        return MathEx.sum(d);
    }

    /**
     * Picks a random observation as new medoid.
     */
    private static <T> T getRandomMedoid(T[] data, T[] medoids) {
        int n = data.length;

        T medoid = data[MathEx.randomInt(n)];
        while (contains(medoids, medoid)) {
            medoid = data[MathEx.randomInt(n)];
        }

        return medoid;
    }

    /**
     * Returns true if the array contains the object.
     */
    private static <T> boolean contains(T[] medoids, T medoid) {
        for (T m : medoids) {
            if (m == medoid) return true;
        }
        return false;
    }
}
