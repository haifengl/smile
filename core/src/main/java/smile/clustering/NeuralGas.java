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

import smile.math.MathEx;
import smile.sort.QuickSort;

/**
 * Neural Gas soft competitive learning algorithm. Neural Gas is inspired
 * by the Self-Organizing Map (SOM) for finding optimal data representations
 * based on feature vectors. The algorithm was coined "Neural Gas" because of
 * the dynamics of the feature vectors during the adaptation process, which
 * distribute themselves like a gas within the data space. Although it is
 * mainly applied where data compression or vector quantization is an issue,
 * it is also used for cluster analysis as a robustly converging alternative
 * to k-means. A prominent extension is the Growing Neural Gas.
 * <p>
 * Compared to SOM, Neural Gas has no topology of a fixed dimensionality
 * (in fact, no topology at all). For each input signal during learning,
 * Neural Gas sorts the neurons of the network according to the distance
 * of their reference vectors to the input signal. Based on this "rank order",
 * neurons are adapted based on the adaptation strength that are decreased
 * according to a fixed schedule.
 * <p>
 * The adaptation step of the Neural Gas can be interpreted as gradient
 * descent on a cost function. By adapting not only the closest feature
 * vector but all of them with a step size decreasing with increasing
 * distance order, compared to k-means, a much more robust convergence
 * of the algorithm can be achieved.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Thomas Martinetz and Klaus Schulten. A "neural gas" network learns topologies. Artificial Neural Networks, 397-402, 1991.</li>
 * <li> T. Martinetz, S. Berkovich, and K. Schulten. "Neural-gas" Network for Vector Quantization and its Application to Time-Series Prediction. IEEE Trans. on Neural Networks, 4(4):558-569, 1993. </li>
 * <li> T. Martinetz and K. Schulten. Topology representing networks. Neural Networks, 7(3):507-522, 1994. </li>
 * </ol>
 * 
 * @see KMeans
 * @see smile.vq.GrowingNeuralGas
 * @see smile.vq.SOM
 * 
 * @author Haifeng Li
 */
public class NeuralGas extends CentroidClustering<double[], double[]> {
    /**
     * Constructor.
     * @param distortion the total distortion.
     * @param centroids the centroids of each cluster.
     * @param y the cluster labels.
     */
    public NeuralGas(double distortion, double[][] centroids, int[] y) {
        super(distortion, centroids, y, MathEx::squaredDistance);
    }

    /**
     * Fits the Neural Gas.
     * @param k the number of neurons/clusters.
     */
    public static NeuralGas fit(double[][] data, int k) {
        return fit(data, k, 30, 0.01, 0.3, 0.05, 25);
    }

    /**
     * Fits the Neural Gas.
     * @param k the number of neurons/clusters.
     * @param range the initial neighborhood range.
     * @param finalRange The final neighborhood range.
     * @param step the initial adaptation step size.
     * @param finalStep the final adaptation step size.
     * @param epochs the number of iterations of processing the (whole) data.
     */
    public static NeuralGas fit(double[][] data, int k, double range, double finalRange, double step, double finalStep, int epochs) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (range <= 0.0) {
            throw new IllegalArgumentException("Invalid initial neighborhood range: " + range);
        }
        
        if (finalRange <= 0.0) {
            throw new IllegalArgumentException("Invalid final neighborhood range: " + finalRange);
        }
        
        if (finalRange >= range) {
            throw new IllegalArgumentException("The final neighborhood range is greater the initial range.");
        }
        
        if (step <= 0.0 || step > 1.0) {
            throw new IllegalArgumentException("Invalid initial step size: " + step);
        }
        
        if (finalStep <= 0.0 || finalStep > 1.0) {
            throw new IllegalArgumentException("Invalid final step size: " + finalStep);
        }
        
        if (finalStep >= step) {
            throw new IllegalArgumentException("The final step size is greater than the initial step size.");
        }
        
        int n = data.length;
        int d = data[0].length;

        double[][] medoids = new double[k][];
        int[] y = new int[n];
        double[] dist = new double[n];
        seed(data, medoids, y, dist, MathEx::squaredDistance);

        dist = new double[k];
        int[] size = new int[k];
        double[][] centroids = new double[k][d];
        updateCentroids(centroids, data, y, size);

        for (int t = 1; t <= epochs; t++) {
            double tf = (double) t / epochs;
            double lambda = range * Math.pow(finalRange / range, tf);
            double eps = step * Math.pow(finalStep / step, tf);

            for (double[] x : data) {
                for (int i = 0; i < k; i++) {
                    dist[i] = MathEx.squaredDistance(centroids[i], x);
                }

                QuickSort.sort(dist, centroids);

                int K = Math.min(k, (int) Math.ceil(5 * lambda));
                for (int i = 0; i < K; i++) {
                    double delta = eps * Math.exp(-i / lambda);
                    double[] centroid = centroids[i];
                    for (int j = 0; j < d; j++) {
                        centroid[j] += delta * (x[j] - centroid[j]);
                    }
                }
            }
        }

        double distortion = assign(y, data, centroids, MathEx::squaredDistance);
        return new NeuralGas(distortion, centroids, y);
    }
}
