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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
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
     * Neurons in the neural network.
     */
    private ArrayList<Neuron> neurons = new ArrayList<>();
    /**
     * Constructor.
     * @param d the dimensionality of signals.
     * @param r the distance radius to activate a neuron for a given signal.
     * @param epsBest the learning rate to update activated neuron.
     * @param epsNeighbor the learning rate to update neighbors of activated neuron.
     * @param edgeLifetime the maximum age of edges.
     */
    public NeuralMap(int d, double r, double epsBest, double epsNeighbor, int edgeLifetime) {
        this.r = r;
        this.epsBest = epsBest;
        this.epsNeighbor = epsNeighbor;
        this.edgeLifetime = edgeLifetime;
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
        Collections.sort(neurons);

        Neuron s1 = neurons.get(0);
        Neuron s2 = neurons.get(1);

        if (s1.distance > r) {
            Neuron neuron = new Neuron(x.clone());
            neurons.add(neuron);

            s1.addEdge(neuron, t);
            neuron.addEdge(s1, t);

            s2.addEdge(neuron, t);
            neuron.addEdge(s2, t);
            return;
        }

        // update s1
        s1.update(x, epsBest);

        boolean addEdge = true;
        for (Edge edge : s1.edges) {
            // Update s1's direct topological neighbors towards x.
            Neuron neighbor = edge.neighbor;
            neighbor.update(x, epsNeighbor);

            // Set the age to zero if s1 and s2 are already connected.
            if (neighbor == s2) {
                edge.age = t;
                s2.setEdgeAge(s1, t);
                addEdge = false;
            }
        }

        // Connect s1 and s2 if they are not neighbor yet.
        if (addEdge) {
            s1.addEdge(s2, t);
            s2.addEdge(s1, t);
            s2.update(x, epsNeighbor);
        }

        // Remove edges with an age larger than the threshold
        for (Iterator<Edge> iter = s1.edges.iterator(); iter.hasNext();) {
            Edge edge = iter.next();
            if (t - edge.age > edgeLifetime) {
                iter.remove();

                Neuron neighbor = edge.neighbor;
                neighbor.removeEdge(s1);
                // Remove a neuron if it has no emanating edges
                if (neighbor.edges.isEmpty()) {
                    neurons.removeIf(neuron -> neuron == edge.neighbor);
                }
            }
        }
    }

    /**
     * Returns the set of neurons.
     */
    public Neuron[] neurons() {
        return neurons.toArray(new Neuron[neurons.size()]);
    }
    
    /**
     * Removes the edges beyond lifetime and neurons without emanating edges.
     */
    public void clean() {
        ArrayList<Neuron> noise = new ArrayList<>();
        for (Neuron neuron : neurons) {
            for (Iterator<Edge> iter = neuron.edges.iterator(); iter.hasNext();) {
                Edge edge = iter.next();
                if (t - edge.age > edgeLifetime) {
                    iter.remove();
                }
            }

            if (neuron.edges.isEmpty()) {
                noise.add(neuron);
            }
        }

        neurons.removeAll(noise);
    }

    @Override
    public Optional<double[]> quantize(double[] x) {
        neurons.stream().parallel().forEach(node -> node.distance(x));
        Collections.sort(neurons);
        return Optional.ofNullable(neurons.get(0).w);
    }
}
