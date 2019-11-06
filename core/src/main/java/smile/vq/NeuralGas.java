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

package smile.vq;

import java.util.Arrays;

import smile.clustering.CentroidClustering;
import smile.math.MathEx;

/**
 * Neural Gas soft competitive learning algorithm. The Neural Gas is inspired
 * by the Self-Organizing Map for finding optimal data representations based on
 * feature vectors. The algorithm was coined "Neural Gas" because of the
 * dynamics of the feature vectors during the adaptation process, which
 * distribute themselves like a gas within the data space. Although it is mainly
 * applied where data compression or vector quantization is an issue,
 * it is also used for cluster analysis as a robustly converging alternative to
 * the k-means clustering. A prominent extension is the Growing Neural Gas.
 * <p>
 * Compared to SOM, neural gas has no topology of a fixed dimensionality
 * (in fact, no topology at all). For each input signal during learning, the
 * neural gas algorithm sorts the neurons of the network according to the
 * distance of their reference vectors to the input signal. Based on this
 * "rank order", neurons are adapted based on the adaptation strength that are
 * decreased according to a fixed schedule.
 * <p>
 * The adaptation step of the Neural Gas can be interpreted as gradient descent
 * on a cost function. By adapting not only the closest feature vector but all
 * of them with a step size decreasing with increasing distance order,
 * compared to k-means clustering, a much more robust convergence of the
 * algorithm can be achieved.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Thomas Martinetz and Klaus Schulten. A "neural gas" network learns topologies. Artificial Neural Networks, 397-402, 1991.</li>
 * <li> T. Martinetz, S. Berkovich, and K. Schulten. "Neural-gas" Network for Vector Quantization and its Application to Time-Series Prediction. IEEE Trans. on Neural Networks, 4(4):558-569, 1993. </li>
 * <li> T. Martinetz and K. Schulten. Topology representing networks. Neural Networks, 7(3):507-522, 1994. </li>
 * </ol>
 * 
 * @see smile.clustering.KMeans
 * @see GrowingNeuralGas
 * @see NeuralMap
 * 
 * @author Haifeng Li
 */
public class NeuralGas extends CentroidClustering<double[], double[]> {
    /**
     * A class representing a node for all neural gas algorithms.
     */
    static class Neuron implements Comparable<Neuron> {
        /**
         * Reference vector.
         */
        double[] w;
        /**
         * The distance between the node and an input signal.
         */
        double dist = Double.MAX_VALUE;

        /**
         * Constructor.
         */
        Neuron(double[] w) {
            this.w = w;
        }

        @Override
        public int compareTo(Neuron o) {
            return Double.compare(dist, o.dist);
        }
    }

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
     * Constructor. Learn the Neural Gas with k neurons.
     * @param k the number of units in the neural gas. It is also the number
     * of clusters.
     */
    public static NeuralGas fit(double[][] data, int k) {
        return fit(data, k, Math.min(10, Math.max(1, k/2)), 0.01, 0.5, 0.005, 25);
    }

    /**
     * Constructor. Learn the Neural Gas with k neurons.
     * @param k the number of units in the neural gas.
     * @param lambda_i the initial value of lambda. lambda_i and lambda_f are
     * used to set the soft learning radius/rate, i.e. determining the number
     * of neural units significantly changing their synaptic weights with
     * each adaptation step.
     * @param lambda_f The final value of lambda.
     * @param eps_i the initial value of epsilon. epsilon_i and epsilon_f
     * are the initial and final learning rate respectively.
     * @param eps_f the final value of epsilon.
     * @param steps the number of iterations. Note that for one iteration, we
     * mean that the learning process goes through the whole dataset.
     */
    public static NeuralGas fit(double[][] data, int k, double lambda_i, double lambda_f, double eps_i, double eps_f, int steps) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (lambda_i <= 0.0) {
            throw new IllegalArgumentException("Invalid initial value of lambda: " + lambda_i);            
        }
        
        if (lambda_f <= 0.0) {
            throw new IllegalArgumentException("Invalid final value of lambda: " + lambda_i);            
        }
        
        if (lambda_f >= lambda_i) {
            throw new IllegalArgumentException("lambda_f is NOT less than lambda_i.");                        
        }
        
        if (eps_i <= 0.0 || eps_i > 1.0) {
            throw new IllegalArgumentException("Invalid initial value of epsilon: " + eps_i);            
        }
        
        if (eps_f <= 0.0 || eps_f > 1.0) {
            throw new IllegalArgumentException("Invalid final value of epsilon: " + eps_i);            
        }
        
        if (eps_f >= eps_i) {
            throw new IllegalArgumentException("eps_f is NOT less than eps_i.");                        
        }
        
        int n = data.length;
        int d = data[0].length;

        double[][] medoids = new double[k][];
        int[] y = new int[n];
        double[] dist = new double[n];
        seed(data, medoids, y, dist, MathEx::squaredDistance);

        int[] size = new int[k];
        double[][] centroids = new double[k][d];
        updateCentroids(centroids, data, y, size);

        Neuron[] nodes = new Neuron[k];
        for (int i = 0; i < k; i++) {
            nodes[i] = new Neuron(centroids[i]);
        }

        for (int t = 0; t < steps; t++) {
            double tf = (double) t / steps;
            double lambda = lambda_i * Math.pow(lambda_f / lambda_i, tf);
            double eps = eps_i * Math.pow(eps_f / eps_i, tf);

            for (double[] signal : data) {
                for (Neuron node : nodes) {
                    node.dist = MathEx.squaredDistance(node.w, signal);
                }

                Arrays.sort(nodes);

                for (int i = 0; i < k; i++) {
                    double delta = eps * Math.exp(-i / lambda);
                    if (delta > 0) {
                        for (int j = 0; j < d; j++) {
                            nodes[i].w[j] += delta * (signal[j] - nodes[i].w[j]);
                        }
                    }
                }
            }
        }

        double distortion = assign(y, data, centroids, MathEx::squaredDistance);
        return new NeuralGas(distortion, centroids, y);
    }
}
