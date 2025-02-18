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
import smile.math.distance.HammingDistance;
import smile.util.AlgoStatus;
import smile.util.IntSet;

/**
 * K-Modes clustering. K-Modes is the binary equivalent for K-Means.
 * The mean update for centroids is replaced by the mode one which is
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
public class KModes {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KModes.class);

    /** Constructor. */
    private KModes() {

    }

    /**
     * Fits k-modes clustering.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @return the model.
     */
    public static CentroidClustering<int[], int[]> fit(int[][] data, int k, int maxIter) {
        return fit(data, new Clustering.Options(k, maxIter));
    }

    /**
     * Fits k-modes clustering.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<int[], int[]> fit(int[][] data, Clustering.Options options) {
        int k = options.k();
        int maxIter = options.maxIter();
        double tol = options.tol();
        var controller = options.controller();
        int n = data.length;
        int d = data[0].length;

        Codec[] codec = IntStream.range(0, d).parallel().mapToObj(j -> {
            int[] x = new int[n];
            for (int i = 0; i < n; i++) x[i] = data[i][j];
            return new Codec(x);
        }).toArray(Codec[]::new);

        var clustering = CentroidClustering.init("K-Modes", data, k, new HammingDistance());
        double distortion = clustering.distortion();
        logger.info("Initial distortion = {}", distortion);

        double diff = Integer.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {
            updateCentroids(clustering, data, codec);
            clustering = clustering.assign(data);
            diff = distortion - clustering.distortion();
            distortion = clustering.distortion();

            logger.info("Iteration {}: distortion = {}", iter, clustering.distortion());
            if (controller != null) {
                controller.submit(new AlgoStatus(iter, distortion));
                if (controller.isInterrupted()) break;
            }
        }

        // In case of early stop, we should recalculate centroids.
        if (diff > 0) {
            updateCentroids(clustering, data, codec);
        }

        return clustering;
    }

    /** Maps column values to compact range. */
    private static class Codec {
        /** The number of unique values. */
        public final int k;
        /** The values in [0, k). */
        public final int[] x;
        /** The map of value to index. */
        public final IntSet encoder;

        public Codec(int[] x) {
            int[] y = MathEx.unique(x);
            Arrays.sort(y);

            this.x = x;
            this.k = y.length;
            this.encoder = new IntSet(y);

            if (y[0] != 0 || y[k-1] != k-1) {
                int n = x.length;
                for (int i = 0; i < n; i++) {
                    x[i] = encoder.indexOf(x[i]);
                }
            }
        }

        /** Returns the original value. */
        public int valueOf(int i) {
            return encoder.valueOf(i);
        }
    }

    /**
     * Calculates the new centroids in the new clusters.
     */
    private static void updateCentroids(CentroidClustering<int[], int[]> clustering, int[][] data, Codec[] codec) {
        int n = data.length;
        int[] group = clustering.group();
        int[][] centroids = clustering.centers();
        int k = centroids.length;
        int d = centroids[0].length;

        IntStream.range(0, k).parallel().forEach(cluster -> {
            int[] centroid = new int[d];
            for (int j = 0; j < d; j++) {
                // constant column
                if (codec[j].k <= 1) continue;

                int[] count = new int[codec[j].k];
                int[] x = codec[j].x;
                for (int i = 0; i < n; i++) {
                    if (group[i] == cluster) {
                        count[x[i]]++;
                    }
                }
                centroid[j] = codec[j].valueOf(MathEx.whichMax(count));
            }
            centroids[cluster] = centroid;
        });
    }
}
