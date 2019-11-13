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

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.IntStream;
import smile.clustering.CentroidClustering;
import smile.graph.AdjacencyMatrix;
import smile.graph.Graph;
import smile.graph.Graph.Edge;
import smile.math.MathEx;
import smile.sort.QuickSort;
import smile.math.TimeFunction;

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
 * @see smile.clustering.KMeans
 * @see GrowingNeuralGas
 * @see SOM
 * 
 * @author Haifeng Li
 */
public class NeuralGas implements VectorQuantizer {
    private static final long serialVersionUID = 2L;

    /**
     * Neural Gas Neuron.
     */
    private static class Neuron implements Serializable {
        /** The weight vector. */
        public final double[] w;
        /** The index of neuron. */
        public final int i;

        /**
         * Constructor.
         * @param i the index of neuron.
         * @param w the weight vector.
         */
        public Neuron(int i, double[] w) {
            this.i = i;
            this.w = w;
        }
    }

    /**
     * The neurons.
     */
    private Neuron[] neurons;
    /**
     * The network of neurons.
     */
    private AdjacencyMatrix graph;
    /**
     * The learning rate function.
     */
    private TimeFunction alpha;
    /**
     * The neighborhood function.
     */
    private TimeFunction theta;
    /**
     * The lifetime of connections.
     */
    private int lifetime;
    /**
     * The distance between a new observation to neurons.
     */
    private double[] dist;
    /**
     * The current iteration.
     */
    private int t = 0;
    /**
     * The threshold to update neuron if alpha * theta > eps.
     */
    private double eps = 1E-7;

    /**
     * Constructor.
     * @param neurons the initial neurons.
     * @param alpha the learning rate function.
     * @param theta the neighborhood function.
     * @param lifetime the neuron connection lifetime, usually the number of
     *                 iterations for one or two epochs.
     */
    public NeuralGas(double[][] neurons, TimeFunction alpha, TimeFunction theta, int lifetime) {
        this.neurons = IntStream.range(0, neurons.length).mapToObj(i -> new Neuron(i, neurons[i].clone())).toArray(Neuron[]::new);
        this.alpha = alpha;
        this.theta = theta;
        this.lifetime = lifetime;
        this.graph = new AdjacencyMatrix(neurons.length);
        this.dist = new double[neurons.length];
    }

    /**
     * Selects random samples as initial neurons of Neural Gas.
     * @param k the number of neurons.
     * @param samples some samples to select initial weight vectors.
     */
    public static double[][] seed(int k, double[][] samples) {
        int n = samples.length;
        int[] y = new int[n];
        double[][] medoids = new double[k][];
        CentroidClustering.seed(samples, medoids, y, MathEx::squaredDistance);

        return medoids;
    }

    /**
     * Returns the neurons.
     */
    public double[][] neurons() {
        Arrays.sort(neurons, (x, y) -> Integer.compare(x.i, y.i));
        return Arrays.stream(neurons).map(neuron -> neuron.w).toArray(double[][]::new);
    }

    /**
     * Returns the network of neurons.
     */
    public Graph network() {
        for (int i = 0; i < neurons.length; i++) {
            for (Edge e : graph.getEdges(i)) {
                if (t - e.weight > lifetime) {
                    graph.setWeight(e.v1, e.v2, 0);
                }
            }
        }

        return graph;
    }

    @Override
    public void update(double[] x) {
        int k = neurons.length;
        int d = x.length;

        IntStream.range(0, neurons.length).parallel().forEach(i -> dist[i] = MathEx.distance(neurons[i].w, x));
        QuickSort.sort(dist, neurons);

        double rate = alpha.of(t);
        for (int i = 0; i < k; i++) {
            double delta = rate * Math.exp(-i/theta.of(t));
            if (delta > eps) {
                double[] w = neurons[i].w;
                for (int j = 0; j < d; j++) {
                    w[j] += delta * (x[j] - w[j]);
                }
            }
        }

        graph.setWeight(neurons[0].i, neurons[1].i, t);

        t = t + 1;
    }

    @Override
    public double[] quantize(double[] x) {
        IntStream.range(0, neurons.length).parallel().forEach(i -> dist[i] = MathEx.distance(neurons[i].w, x));
        return neurons[MathEx.whichMin(dist)].w;
    }
}
