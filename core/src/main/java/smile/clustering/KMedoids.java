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

import java.util.Arrays;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.util.AlgoStatus;

/**
 * K-Medoids clustering based on randomized search (CLARANS). The k-medoids
 * algorithm is an adaptation of the k-means algorithm. Rather than calculate
 * the mean of the items in each cluster, a representative item, or medoid,
 * is chosen for each cluster at each iteration. In CLARANS, the process of
 * finding k medoids from n objects is viewed abstractly as searching through
 * a certain graph. In the graph, a node is represented by a set of k objects
 * as selected medoids. Two nodes are neighbors if their sets differ by only
 * one object. In each iteration, CLARANS considers a set of randomly chosen
 * neighbor nodes as candidate of new medoids. We will move to the neighbor
 * node if the neighbor is a better choice for medoids. Otherwise, a local
 * optima is discovered. The entire process is repeated multiple time to
 * find better.
 * <p>
 * CLARANS has two parameters: the maximum number of neighbors examined
 * (maxNeighbor) and the number of local minima obtained (numLocal). The
 * higher the value of maxNeighbor, the closer is CLARANS to PAM, and the
 * longer is each search of a local minima. But the quality of such a local
 * minima is higher and fewer local minima needs to be obtained.
 * <p>
 * The runtime is proportional to numLocal. As for the relative quality,
 * there is an improvement from numLocal = 1 to numLocal = 2. Performing
 * a second search for a local minimum seems to reduce the impact of
 * "unlucky" randomness that may occur in just one search. However,
 * setting numLocal larger than 2 is not cost-effective, as there is
 * little increase in quality.
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
public class KMedoids<T> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KMedoids.class);

    /**
     * Fits k-medoids clustering.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @return the model.
     */
    public static <T> CentroidClustering<T, T> fit(T[] data, Distance<T> distance, int k) {
        return fit(data, distance, new Clustering.Options(k, 2, 0.0125, null));
    }

    /**
     * Fits k-medoids clustering.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters. The parameter maxIter is used as numLocal
     *                while the parameter tol will be interpreted as the ratio to
     *                calculate maxNeighbor = tol * n * (n-k).
     * @return the model.
     */
    public static <T> CentroidClustering<T, T> fit(T[] data, Distance<T> distance, Clustering.Options options) {
        int n = data.length;
        int k = options.k();
        if (k >= n) {
            throw new IllegalArgumentException("Too large k: " + k);
        }

        var controller = options.controller();
        int numLocal = Math.min(3, options.maxIter());
        int maxNeighbor = (int) Math.round(options.tol() * k * (n - k));
        int minmax = Math.min(100, k * (n - k));
        maxNeighbor = Math.max(minmax, maxNeighbor);
        if (maxNeighbor > n) {
            throw new IllegalArgumentException("Too large maxNeighbor: " + maxNeighbor);
        }

        double best = Double.MAX_VALUE;
        CentroidClustering<T, T> result = null;
        for (int iter = 1; iter <= numLocal; iter++) {
            T[] medoids = Arrays.copyOf(data, k);
            var clustering = CentroidClustering.init("K-Medoids", data, medoids, distance);
            double distortion = clustering.distortion();
            int[] group = clustering.group();
            double[] proximity = clustering.proximity();

            T[] centers = medoids.clone();
            int[] y = new int[n];
            double[] d = new double[n];

            for (int neighborCount = 1; neighborCount <= maxNeighbor; neighborCount++) {
                System.arraycopy(medoids, 0, centers, 0, k);
                System.arraycopy(group, 0, y, 0, n);
                System.arraycopy(proximity, 0, d, 0, n);

                double loss = randomSearch(data, centers, y, d, distance);
                if (loss < distortion) {
                    System.arraycopy(centers, 0, medoids, 0, k);
                    System.arraycopy(y, 0, group, 0, n);
                    System.arraycopy(d, 0, proximity, 0, n);
                    distortion = loss;

                    logger.info("Iteration {}: random search = {}, distortion = {} ", iter, neighborCount, distortion);
                    neighborCount = 0;
                }
            }

            if (distortion < best) {
                best = distortion;
                result = new CentroidClustering<>("K-Medoids", medoids, distance, group, proximity);
            }

            if (controller != null) {
                controller.submit(new AlgoStatus(iter, distortion));
                if (controller.isInterrupted()) break;
            }
        }

        return result;
    }

    /**
     * Picks a random neighbor which differs in only one medoid with current clusters.
     */
    private static <T> double randomSearch(T[] data, T[] medoids, int[] y, double[] d, Distance<T> distance) {
        int n = data.length;
        int k = medoids.length;

        int cluster = MathEx.randomInt(k);
        T medoid = getRandomMedoid(data, medoids);
        medoids[cluster] = medoid;

        IntStream.range(0, n).parallel().forEach(i -> {
            double dist = distance.applyAsDouble(data[i], medoid);
            dist *= dist;
            if (d[i] > dist) {
                y[i] = cluster;
                d[i] = dist;
            } else if (y[i] == cluster) {
                d[i] = dist;
                for (int j = 0; j < k; j++) {
                    if (j != cluster) {
                        dist = distance.applyAsDouble(data[i], medoids[j]);
                        dist *= dist;
                        if (d[i] > dist) {
                            d[i] = dist;
                            y[i] = j;
                        }
                    }
                }
            }
        });

        return MathEx.mean(d);
    }

    /**
     * Picks a random observation as new medoid.
     */
    private static <T> T getRandomMedoid(T[] data, T[] medoids) {
        int n = data.length;
        T medoid = data[MathEx.randomInt(n)];
        while (CentroidClustering.contains(medoid, medoids)) {
            medoid = data[MathEx.randomInt(n)];
        }

        return medoid;
    }
}
