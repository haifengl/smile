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
import smile.vq.hebb.Neuron;
import smile.vq.hebb.Edge;

/**
 * NeuralMap is an efficient competitive learning algorithm inspired by growing
 * neural gas and BIRCH. Like growing neural gas, NeuralMap has the ability to
 * add and delete neurons with competitive Hebbian learning. Edges exist between
 * neurons close to each other. Such edges are intended place holders for
 * localized data distribution. Such edges also help to locate distinct clusters
 * (those clusters are not connected by edges).
 *
 * @see NeuralGas
 * @see GrowingNeuralGas
 * @see BIRCH
 * 
 * @author Haifeng Li
 */
public class NeuralMap implements VectorQuantizer {
    private static final long serialVersionUID = 2L;

    /**
     * The number of signals processed so far.
     */
    private int t = 0;
    /**
     * The distance radius to activate a neuron for a given signal.
     */
    private double r;
    /**
     * The maximum age of edges.
     */
    private int edgeLifetime = 50;
    /**
     * The learning rate to update nearest neuron.
     */
    private double epsBest = 0.2;
    /**
     * The learning to update neighbors of nearest neuron.
     */
    private double epsNeighbor = 0.006;
    /**
     * Decrease the freshness of all neurons by multiply them with beta.
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
     * @param r the distance threshold to activate the nearest neuron of a signal.
     * @param epsBest the learning rate to update activated neuron.
     * @param epsNeighbor the learning rate to update neighbors of activated neuron.
     * @param edgeLifetime the maximum age of edges.
     * @param beta decrease the freshness of all neurons by multiply them with beta.
     */
    public NeuralMap(double r, double epsBest, double epsNeighbor, int edgeLifetime, double beta) {
        this.r = r;
        this.epsBest = epsBest;
        this.epsNeighbor = epsNeighbor;
        this.edgeLifetime = edgeLifetime;
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

        if (s1.distance > r) {
            Neuron neuron = new Neuron(x.clone());
            neurons.add(neuron);
            return;
        }

        if (s2.distance > r) {
            Neuron neuron = new Neuron(x.clone());
            neurons.add(neuron);

            s1.addEdge(neuron);
            neuron.addEdge(s1);
            return;
        }

        // update s1
        s1.update(x, epsBest);
        // Increase the freshness of neuron.
        s1.counter += 1;
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
                    neurons.removeIf(neuron -> neuron == edge.neighbor);
                }
            }
        }

        // Decrease all error variables.
        for (Neuron neuron : neurons) {
            neuron.counter *= beta;
        }
    }

    /**
     * Returns the set of neurons.
     */
    public Neuron[] neurons() {
        return neurons.toArray(new Neuron[neurons.size()]);
    }
    
    /**
     * Removes staled neurons and the edges beyond lifetime.
     * Neurons without emanating edges will be removed too.
     * @param eps the freshness threshold of neurons. It should
     *            be a small value (e.g. 1E-7).
     */
    public void clear(double eps) {
        ArrayList<Neuron> noise = new ArrayList<>();
        for (Neuron neuron : neurons) {
            if (neuron.counter < eps) {
                for (Edge edge : neuron.edges) {
                    edge.neighbor.removeEdge(neuron);
                }
                neuron.edges.clear();
            } else {
                for (Iterator<Edge> iter = neuron.edges.iterator(); iter.hasNext(); ) {
                    Edge edge = iter.next();
                    if (edge.age > edgeLifetime) {
                        edge.neighbor.removeEdge(neuron);
                        iter.remove();
                    }
                }
            }

            if (neuron.edges.isEmpty()) {
                noise.add(neuron);
            }
        }

        neurons.removeAll(noise);
    }

    @Override
    public double[] quantize(double[] x) {
        neurons.stream().parallel().forEach(node -> node.distance(x));

        Neuron bmu = neurons.get(0);
        for (Neuron neuron : neurons) {
            if (neuron.distance < bmu.distance) {
                bmu = neuron;
            }
        }

        return bmu.w;
    }
}
