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

import java.util.stream.IntStream;
import smile.classification.ClassLabels;
import smile.math.MathEx;
import smile.math.distance.HammingDistance;

/**
 * K-Modes clustering. K-Modes is the binary equivalent for K-Means.
 * The mean update for centroids is replace by the mode one which is
 * a majority vote among element of each cluster.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Joshua Zhexue Huang. Clustering Categorical Data with k-Modes.</li>
 * </ol>
 *
 * @see KMeans
 *
 * @author Haifeng Li
 */
public class KModes extends CentroidClustering<int[], int[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KModes.class);

    /**
     * Constructor.
     * @param distortion the total distortion.
     * @param centroids the centroids of each cluster.
     * @param y the cluster labels.
     */
    public KModes(double distortion, int[][] centroids, int[] y) {
        super(distortion, centroids, y);
    }

    @Override
    public double distance(int[] x, int[] y) {
        return HammingDistance.d(x, y);
    }

    /**
     * Fits k-modes clustering.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     */
    public static KModes fit(int[][] data, int k) {
        return fit(data, k, 100);
    }

    /**
     * Fits k-modes clustering.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     */
    public static KModes fit(int[][] data, int k, int maxIter) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = data.length;
        int d = data[0].length;

        ClassLabels[] codec = IntStream.range(0, d).parallel().mapToObj(j -> {
            int[] x = new int[n];
            for (int i = 0; i < n; i++) x[i] = data[i][j];
            return ClassLabels.fit(x);
        }).toArray(ClassLabels[]::new);

        int[] y = new int[n];
        int[][] medoids = new int[k][];
        int[][] centroids = new int[k][d];

        double distortion = MathEx.sum(seed(data, medoids, y, HammingDistance::d));
        logger.info(String.format("Distortion after initialization: %d", (int) distortion));

        double diff = Integer.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > 0; iter++) {
            updateCentroids(centroids, data, y, codec);

            double wcss = assign(y, data, centroids, HammingDistance::d);
            logger.info(String.format("Distortion after %3d iterations: %d", iter, (int) wcss));

            diff = distortion - wcss;
            distortion = wcss;
        }

        // In case of early stop, we should recalculate centroids.
        if (diff > 0) {
            updateCentroids(centroids, data, y, codec);
        }

        return new KModes(distortion, centroids, y);
    }

    /**
     * Calculates the new centroids in the new clusters.
     */
    private static void updateCentroids(int[][] centroids, int[][] data, int[] y, ClassLabels[] codec) {
        int n = data.length;
        int k = centroids.length;
        int d = centroids[0].length;

        IntStream.range(0, k).parallel().forEach(cluster -> {
            int[] centroid = centroids[cluster];
            for (int j = 0; j < d; j++) {
                int[] count = new int[codec[j].k];
                int[] x = codec[j].y;
                for (int i = 0; i < n; i++) {
                    if (y[i] == cluster) {
                        count[x[i]]++;
                    }
                }
                centroid[j] = codec[j].labels.valueOf(MathEx.whichMax(count));
            }
        });
    }
}
