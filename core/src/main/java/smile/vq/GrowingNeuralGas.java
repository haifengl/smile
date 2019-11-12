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

import java.util.*;
import smile.sort.HeapSelect;
import smile.vq.hebb.Edge;
import smile.vq.hebb.Neuron;

/**
 * Growing Neural Gas. As an extension of Neural Gas, Growing Neural Gas
 * can add and delete nodes during algorithm execution.  The growth mechanism
 * is based on growing cell structures and competitive Hebbian learning.
 * <p>
 * Compared to Neural Gas, GNG has the following distinctions:
 * <ul>
 * <li> The system has the ability to add and delete nodes. </li>
 * <li> Local Error measurements are noted at each step helping it to locally
 * insert/delete nodes.</li>
 * <li> Edges are connected between nodes, so a sufficiently old edges is
 * deleted. Such edges are intended place holders for localized data distribution.</li>
 * <li> Such edges also help to locate distinct clusters (those clusters are
 * not connected by edges).</li>
 * </ul>
 * 
 * <h2>References</h2>
 * <ol>
 * <li> B. Fritzke. A growing neural gas network learns topologies. NIPS, 1995.</li>
 * </ol>
 * 
 * @see smile.clustering.KMeans
 * @see NeuralGas
 * @see NeuralMap
 * 
 * @author Haifeng Li
 */
public class GrowingNeuralGas implements VectorQuantizer {
    private static final long serialVersionUID = 2L;

    /**
     * The dimensionality of signals.
     */
    private final int d;
    /**
     * The number of signals processed so far.
     */
    private int t = 0;
    /**
     * The learning rate to update best matching neuron.
     */
    private double epsBest = 0.2;
    /**
     * The learning rate to update neighbors of best matching neuron.
     */
    private double epsNeighbor = 0.006;
    /**
     * The maximum age of edges.
     */
    private int edgeLifetime = 50;
    /**
     * If the number of input signals so far is an integer multiple
     * of lambda, insert a new neuron.
     */
    private int lambda = 100;
    /**
     * Decrease error variables by multiplying them with alpha
     * during inserting a new neuron.
     */
    private double alpha = 0.5;
    /**
     * Decrease all error variables by multiply them with beta.
     */
    private double beta = 0.995;
    /**
     * Neurons in the neural network.
     */
    private ArrayList<Neuron> neurons = new ArrayList<>();
    /**
     * The workspace to find nearest neighbors.
     */
    private Neuron[] top2 = new Neuron[2];

    /**
     * Constructor.
     * @param d the dimensionality of signals.
     */
    public GrowingNeuralGas(int d) {
        this.d = d;
    }

    /**
     * Constructor.
     * @param d the dimensionality of signals.
     * @param epsBest the learning rate to update best matching neuron.
     * @param epsNeighbor the learning rate to update neighbors of best matching neuron.
     * @param edgeLifetime the maximum age of edges.
     * @param lambda if the number of input signals so far is an integer multiple
     *               of lambda, insert a new neuron.
     * @param alpha decrease error variables by multiplying them with alpha
     *              during inserting a new neuron.
     * @param beta decrease all error variables by multiply them with beta.
     */
    public GrowingNeuralGas(int d, double epsBest, double epsNeighbor, int edgeLifetime, int lambda, double alpha, double beta) {
        this.d = d;
        this.epsBest = epsBest;
        this.epsNeighbor = epsNeighbor;
        this.edgeLifetime = edgeLifetime;
        this.lambda = lambda;
        this.alpha = alpha;
        this.beta = beta;
    }

    @Override
    public void update(double[] x) {
        t++;

        if (neurons.size() < 2) {
            neurons.add(new Neuron(x.clone()));
            return;
        }

        // Find the nearest (s1) and second nearest (s2) neuron to x.
        neurons.stream().parallel().forEach(neuron -> neuron.distance(x));

        Arrays.fill(top2, null);
        HeapSelect<Neuron> heap = new HeapSelect<>(top2);
        for (Neuron neuron : neurons) {
            heap.add(neuron);
        }

        Neuron s1 = top2[1];
        Neuron s2 = top2[0];

        // update s1
        s1.update(x, epsBest);
        // update local counter of squared distance
        s1.counter += s1.distance * s1.distance;
        // Increase the edge of all edges emanating from s1.
        s1.age();

        boolean addEdge = true;
        for (Edge edge : s1.edges) {
            // Update s1's direct topological neighbors towards x.
            Neuron neighbor = edge.neighbor;
            neighbor.update(x, epsNeighbor);

            // Set the age to zero if s1 and s2 are already connected.
            if (neighbor == s2) {
                edge.age = 0;
                s2.setEdgeAge(s1, 0);
                addEdge = false;
            }
        }

        // Connect s1 and s2 if they are not neighbor yet.
        if (addEdge) {
            s1.addEdge(s2);
            s2.addEdge(s1);
            s2.update(x, epsNeighbor);
        }

        // Remove edges with an age larger than the threshold
        for (Iterator<Edge> iter = s1.edges.iterator(); iter.hasNext();) {
            Edge edge = iter.next();
            if (edge.age > edgeLifetime) {
                iter.remove();

                Neuron neighbor = edge.neighbor;
                neighbor.removeEdge(s1);
                // Remove a neuron if it has no emanating edges
                if (neighbor.edges.isEmpty()) {
                    neurons.removeIf(neuron -> neuron == neighbor);
                }
            }
        }

        // Add a new neuron if the number of input signals processed so far
        // is an integer multiple of lambda.
        if (t % lambda == 0) {
            // Determine the neuron with the maximum accumulated error.
            Neuron q = neurons.get(0);
            for (Neuron neuron : neurons) {
                if (neuron.counter > q.counter) {
                    q = neuron;
                }
            }

            // Find the neighbor of q with the largest error variable.
            Neuron f = q.edges.get(0).neighbor;
            for (Edge edge : q.edges) {
                if (edge.neighbor.counter > f.counter) {
                    f = edge.neighbor;
                }
            }

            // Decrease the error variables of q and f.
            q.counter *= alpha;
            f.counter *= alpha;

            // Insert a new neuron halfway between q and f.
            double[] w = new double[d];
            for (int i = 0; i < d; i++) {
                w[i] += (q.w[i] + f.w[i]) / 2;
            }

            Neuron r = new Neuron(w, q.counter);
            neurons.add(r);

            // Remove the connection (q, f) and add connections (q, r) and (r, f)
            q.removeEdge(f);
            f.removeEdge(q);
            q.addEdge(r);
            f.addEdge(r);
            r.addEdge(q);
            r.addEdge(f);
        }

        // Decrease all error variables.
        for (Neuron neuron : neurons) {
            neuron.counter *= beta;
        }
    }

    /**
     * Returns the neurons in the network.
     * @return the neurons in the network.
     */
    public Neuron[] neurons() {
        return neurons.toArray(new Neuron[neurons.size()]);
    }

    @Override
    public double[] quantize(double[] x) {
        neurons.stream().parallel().forEach(neuron -> neuron.distance(x));

        Neuron bmu = neurons.get(0);
        for (Neuron neuron : neurons) {
            if (neuron.distance < bmu.distance) {
                bmu = neuron;
            }
        }

        return bmu.w;
    }
}
