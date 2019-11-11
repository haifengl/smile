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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import smile.math.MathEx;
import smile.neighbor.MutableLSH;
import smile.neighbor.Neighbor;
import smile.vq.hebb.Neuron;
import smile.vq.hebb.Edge;

/**
 * NeuralMap is an efficient competitive learning algorithm inspired by growing
 * neural gas and BIRCH. Like growing neural gas, NeuralMap has the ability to
 * add and delete neurons with competitive Hebbian learning. Edges exist between
 * neurons close to each other. Such edges are intended place holders for
 * localized data distribution. Such edges also help to locate distinct clusters
 * (those clusters are not connected by edges). NeuralMap employs Locality-Sensitive
 * Hashing to speedup the learning while BIRCH uses balanced CF trees.
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
    private MutableLSH<Neuron> lsh;

    /**
     * Constructor.
     * @param d the dimensionality of signals.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions.
     * @param r the distance radius to activate a neuron for a given signal.
     * @param epsBest the learning rate to update activated neuron.
     * @param epsNeighbor the learning rate to update neighbors of activated neuron.
     * @param edgeLifetime the maximum age of edges.
     */
    public NeuralMap(int d, int L, int k, double r, double epsBest, double epsNeighbor, int edgeLifetime) {
        this.r = r;
        this.lsh = new MutableLSH<>(d, L, k, 4 * r);
        this.epsBest = epsBest;
        this.epsNeighbor = epsNeighbor;
        this.edgeLifetime = edgeLifetime;
    }

    /**
     * Update the network with a new signal.
     */
    public void update(double[] x) {
        t++;

        // Find the nearest (s1) and second nearest (s2) neuron to x.
        Neighbor<double[], Neuron>[] top2 = lsh.knn(x, 2);

        if (top2.length == 0 || top2[0].distance > r) {
            double[] w = x.clone();
            Neuron neuron = new Neuron(w);
            lsh.put(w, neuron);

            if (top2.length > 0) {
                Neuron s1 = top2[0].value;
                s1.addEdge(neuron, t);
                neuron.addEdge(s1, t);
            }
            return;
        }

        // Find the nearest (s1) and second nearest (s2) neuron to x.
        Neuron s1 = top2[0].value;
        Neuron s2 = top2.length == 2 ? top2[1].value : null;

        // update s1
        lsh.remove(s1.w, s1);
        s1.update(x, epsBest);
        lsh.put(s1.w, s1);

        boolean addEdge = true;
        for (Edge edge : s1.edges) {
            // Update s1's direct topological neighbors towards x.
            Neuron neighbor = edge.neighbor;
            lsh.remove(neighbor.w, neighbor);
            neighbor.update(x, epsNeighbor);
            lsh.put(neighbor.w, neighbor);

            // Set the age to zero if s1 and s2 are already connected.
            if (neighbor == s2) {
                edge.age = t;
                s2.setEdgeAge(s1, t);
                addEdge = false;
            }
        }

        // Connect s1 and s2 if they are not neighbor yet.
        if (addEdge && s2 != null) {
            s1.addEdge(s2, t);
            s2.addEdge(s1, t);

            lsh.remove(s2.w, s2);
            s2.update(x, epsNeighbor);
            lsh.put(s2.w, s2);
        }

        // Remove edges with an age larger than the threshold
        /*
        for (Iterator<Edge> iter = s1.edges.iterator(); iter.hasNext();) {
            Edge edge = iter.next();
            if (t - edge.age > edgeLifetime) {
                iter.remove();

                Neuron neighbor = edge.neighbor;
                neighbor.removeEdge(s1);
                // Remove a neuron if it has no emanating edges
                if (neighbor.edges.isEmpty()) {
                    lsh.remove(neighbor.w, neighbor);
                }
            }
        }
         */
    }

    /**
     * Returns the set of neurons.
     */
    public Neuron[] neurons() {
        List<Neuron> neurons = lsh.values();
        return neurons.toArray(new Neuron[neurons.size()]);
    }
    
    /**
     * Removes the edges beyond lifetime and neurons without emanating edges.
     */
    public void clean() {
        ArrayList<Neuron> noise = new ArrayList<>();
        for (Neuron neuron : lsh.values()) {
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

        for (Neuron neuron : noise) {
            lsh.remove(neuron.w, neuron);
        }
    }

    @Override
    public Optional<double[]> quantize(double[] x) {
        return Optional.ofNullable(lsh.nearest(x)).map(neighbor -> neighbor.key);
    }
}
