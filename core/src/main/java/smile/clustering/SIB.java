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
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.util.SparseArray;

/**
 * The Sequential Information Bottleneck algorithm. SIB clusters co-occurrence
 * data such as text documents vs words. SIB is guaranteed to converge to a local
 * maximum of the information. Moreover, the time and space complexity are
 * significantly improved in contrast to the agglomerative IB algorithm.
 * <p>
 * In analogy to K-Means, SIB's update formulas are essentially same as the
 * EM algorithm for estimating finite Gaussian mixture model by replacing
 * regular Euclidean distance with Kullback-Leibler divergence, which is
 * clearly a better dissimilarity measure for co-occurrence data. However,
 * the common batch updating rule (assigning all instances to nearest centroids
 * and then updating centroids) of K-Means won't work in SIB, which has
 * to work in a sequential way (reassigning (if better) each instance then
 * immediately update related centroids). It might be because K-L divergence
 * is very sensitive and the centroids may be significantly changed in each
 * iteration in batch updating rule.
 * <p>
 * Note that this implementation has a little difference from the original
 * paper, in which a weighted Jensen-Shannon divergence is employed as a
 * criterion to assign a randomly-picked sample to a different cluster.
 * However, this doesn't work well in some cases as we experienced probably
 * because the weighted JS divergence gives too much weight to clusters which
 * is much larger than a single sample. In this implementation, we instead
 * use the regular/unweighted Jensen-Shannon divergence.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> N. Tishby, F.C. Pereira, and W. Bialek. The information bottleneck method. 1999.</li>
 * <li> N. Slonim, N. Friedman, and N. Tishby. Unsupervised document classification using sequential information maximization. ACM SIGIR, 2002.</li>
 * <li>Jaakko Peltonen, Janne Sinkkonen, and Samuel Kaski. Sequential information bottleneck for finite data. ICML, 2004.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class SIB {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SIB.class);

    /**
     * Clustering data into k clusters up to 100 iterations.
     * @param data the sparse normalized co-occurrence dataset of which each
     *             row is an observation of which the sum is 1.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @return the model.
     */
    public static CentroidClustering<double[], SparseArray> fit(SparseArray[] data, int k, int maxIter) {
        return fit(data, new Clustering.Options(k, maxIter));
    }

    /**
     * Clustering data into k clusters.
     * @param data the sparse normalized co-occurrence dataset of which each
     *             row is an observation of which the sum is 1.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], SparseArray> fit(SparseArray[] data, Clustering.Options options) {
        int k = options.k();
        int maxIter = options.maxIter();
        int n = data.length;
        int d = 1 + Arrays.stream(data).flatMapToInt(SparseArray::indexStream).max().orElse(0);

        ToDoubleBiFunction<SparseArray, SparseArray> distance = MathEx::JensenShannonDivergence;
        var clustering = CentroidClustering.init("SIB", data, k, distance);
        logger.info("Initial distortion = {}", clustering.distortion());

        int[] size = clustering.size();
        int[] group = clustering.group();
        double[][] centroids = new double[k][d];
        IntStream.range(0, k).parallel().forEach(cluster -> {
            for (int i = 0; i < n; i++) {
                if (group[i] == cluster) {
                    size[cluster]++;
                    for (SparseArray.Entry e : data[i]) {
                        centroids[cluster][e.index()] += e.value();
                    }
                }
            }

            for (int j = 0; j < d; j++) {
                centroids[cluster][j] /= size[cluster];
            }
        });

        for (int iter = 1, reassignment = n; iter <= maxIter && reassignment > 0; iter++) {
            reassignment = 0;

            for (int i = 0; i < n; i++) {
                int c = group[i];
                double nearest = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    double divergence = MathEx.JensenShannonDivergence(data[i], centroids[j]);
                    if (nearest > divergence) {
                        nearest = divergence;
                        c = j;
                    }
                }

                if (c != group[i]) {
                    int o = group[i];
                    for (int j = 0; j < d; j++) {
                        centroids[c][j] *= size[c];
                        centroids[o][j] *= size[o];
                    }

                    for (SparseArray.Entry e : data[i]) {
                        int j = e.index();
                        double p = e.value();
                        centroids[c][j] += p;
                        centroids[o][j] -= p;
                        if (centroids[o][j] < 0) {
                            centroids[o][j] = 0;
                        }
                    }

                    size[o]--;
                    size[c]++;

                    for (int j = 0; j < d; j++) {
                        centroids[c][j] /= size[c];
                    }

                    if (size[o] > 0) {
                        for (int j = 0; j < d; j++) {
                            centroids[o][j] /= size[o];
                        }
                    }

                    group[i] = c;
                    reassignment++;
                }
            }
            logger.info("Iteration {}: assignments = {}", iter, reassignment);
        }

        var proximity = clustering.proximity();
        double distortion = IntStream.range(0, n).parallel().mapToDouble(i -> {
            double dist = MathEx.JensenShannonDivergence(data[i], centroids[group[i]]);
            dist *= dist;
            proximity[i] = dist;
            return dist;
        }).sum() / n;
        logger.info("Final distortion: {}", distortion);
        return new CentroidClustering<>("SIB", centroids, MathEx::JensenShannonDivergence, group, proximity);
    }
}
