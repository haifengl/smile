/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.vq;

import java.util.Arrays;

import smile.clustering.ClusteringDistance;
import smile.clustering.PartitionClustering;
import smile.math.Math;

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
public class NeuralGas extends PartitionClustering<double[]> {
    /**
     * The total distortion.
     */
    double distortion;
    /**
     * The centroids of each cluster.
     */
    double[][] centroids;

    /**
     * A class representing a node for all neural gas algorithms.
     */
    class Neuron implements Comparable<Neuron> {
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
            return (int) Math.signum(dist - o.dist);
        }
    }

    /**
     * Constructor. Learn the Neural Gas with k neurons.
     * @param k the number of units in the neural gas. It is also the number
     * of clusters.
     */
    public NeuralGas(double[][] data, int k) {
        this(data, k, Math.min(10, Math.max(1, k/2)), 0.01, 0.5, 0.005, 25);
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
    public NeuralGas(double[][] data, int k, double lambda_i, double lambda_f, double eps_i, double eps_f, int steps) {
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
        this.k = k;

        // We use k-means++ seeding method to initialize neurons.
        y = seed(data, k, ClusteringDistance.EUCLIDEAN);

        size = new int[k];
        for (int i = 0; i < n; i++) {
            size[y[i]]++;
        }

        centroids = new double[k][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                centroids[y[i]][j] += data[i][j];
            }
        }

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < d; j++) {
                centroids[i][j] /= size[i];
            }
        }

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
                    node.dist = Math.squaredDistance(node.w, signal);
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

        distortion = 0.0;
        for (int i = 0; i < n; i++) {
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < k; j++) {
                double dist = Math.squaredDistance(data[i], centroids[j]);
                if (nearest > dist) {
                    y[i] = j;
                    nearest = dist;
                }
            }
            distortion += nearest;
        }

        Arrays.fill(size, 0);

        for (int i = 0; i < data.length; i++) {
            size[y[i]]++;
        }
    }

    /**
     * Returns the distortion.
     */
    public double distortion() {
        return distortion;
    }

    /**
     * Returns the centroids/neurons.
     */
    public double[][] centroids() {
        return centroids;
    }

    /**
     * Returns the centroids/neurons.
     */
    public double[][] neurons() {
        return centroids;
    }

    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label, which is the index of nearest centroid.
     */
    @Override
    public int predict(double[] x) {
        double minDist = Double.MAX_VALUE;
        int bestCluster = 0;

        for (int i = 0; i < k; i++) {
            double dist = Math.squaredDistance(x, centroids[i]);
            if (dist < minDist) {
                minDist = dist;
                bestCluster = i;
            }
        }

        return bestCluster;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("Neural Gas distortion: %.5f%n", distortion));
        sb.append(String.format("Clusters of %d data points of dimension %d:%n", y.length, centroids[0].length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }
        
        return sb.toString();
    }
}
