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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import smile.clustering.Clustering;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.Linkage;
import smile.clustering.linkage.UPGMALinkage;
import smile.sort.HeapSelect;
import smile.math.MathEx;

/**
 * Growing Neural Gas. As an extension of Neural Gas, Growing Neural Gas
 * can add and delete nodes during algorithm execution.  The growth mechanism
 * is based on growing cell structures and competitive Hebbian learning.
 * <p>
 * Compared to Neural Gas, GNG has the following distinctions:
 * <ul>
 * <li> The system has the ability to add and delete nodes.
 * <li> Local Error measurements are noted at each step helping it to locally
 * insert/delete nodes.
 * <li> Edges are connected between nodes, so a sufficiently old edges is
 * deleted. Such edges are intended place holders for localized data distribution.
 * <li> Such edges also help to locate distinct clusters (those clusters are
 * not connected by edges).
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
public class GrowingNeuralGas implements Clustering<double[]> {
    /**
     * The neuron vertex in the growing neural gas network.
     */
    public static class Neuron {
        /**
         * Reference vector.
         */
        public final double[] w;
        /**
         * Direct connected neighbors.
         */
        public final Neuron[] neighbors;
        
        /**
         * Constructor.
         */
        public Neuron(double[] w, Neuron[] neighbors) {
            this.w = w;
            this.neighbors = neighbors;
        }
    }

    /**
     * Connection between neurons.
     */
    class Edge {
        /**
         * The end of an edges.
         */
        Node a;
        /**
         * The other end of an edges.
         */
        Node b;
        /**
         * The age of this edges.
         */
        int age = 0;
        /**
         * Constructor.
         */
        Edge(Node a, Node b) {
            this.a = a;
            this.b = b;
        }
    }

    /**
     * A class representing a neuron.
     */
    class Node implements Comparable<Node> {
        /**
         * The id of neuron.
         */
        int id;
        /**
         * Reference vector.
         */
        double[] w;
        /**
         * The distance between the neuron and an input signal.
         */
        double dist = Double.MAX_VALUE;
        /**
         * Local error measurement.
         */
        double error = 0.0;
        /**
         * Edges to neighbors.
         */
        LinkedList<Edge> edges;

        /**
         * Constructor.
         */
        Node(double[] w) {
            this.w = w;
            edges = new LinkedList<>();
            id = m++;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(dist, o.dist);
        }
    }

    /**
     * The dimensionality of signals.
     */
    private final int d;
    /**
     * The number of signals processed so far.
     */
    private int n = 0;
    /**
     * The number of neurons created so far.
     */
    private int m;
    /**
     * The fraction to update nearest neuron.
     */
    private double epsBest = 0.05;
    /**
     * The fraction to update neighbors of nearest neuron.
     */
    private double epsNeighbor = 0.0006;
    /**
     * The maximum age of edges.
     */
    private int maxEdgeAge = 88;
    /**
     * If the number of input signals so far is an integer multiple
     * of lambda, insert a new neuron.
     */
    private int lambda = 300;
    /**
     * Decrease error variables by multiplying them with alpha
     * during inserting a new neuron.
     */
    private double alpha = 0.5;
    /**
     * Decrease all error variables by multiply them with de.
     */
    private double beta = 0.9995;
    /**
     * Neurons in the neural network.
     */
    private LinkedList<Node> nodes = new LinkedList<>();
    /**
     * Cluster labels of neurons.
     */
    private int[] y;

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
     * @param epsBest the fraction to update nearest neuron.
     * @param epsNeighbor the fraction to update neighbors of nearest neuron.
     * @param maxEdgeAge the maximum age of edges.
     * @param lambda if the number of input signals so far is an integer multiple
     * of lambda, insert a new neuron.
     * @param alpha decrease error variables by multiplying them with alpha
     * during inserting a new neuron.
     * @param beta decrease all error variables by multiply them with beta.
     */
    public GrowingNeuralGas(int d, double epsBest, double epsNeighbor, int maxEdgeAge, int lambda, double alpha, double beta) {
        this.d = d;
        this.epsBest = epsBest;
        this.epsNeighbor = epsNeighbor;
        this.maxEdgeAge = maxEdgeAge;
        this.lambda = lambda;
        this.alpha = alpha;
        this.beta = beta;
    }

    /**
     * Returns the neurons in the network.
     * @return the neurons in the network. 
     */
    public Neuron[] neurons() {
        HashMap<Integer, Neuron> hash = new HashMap<>();
        Neuron[] neurons = new Neuron[nodes.size()];
        
        int i = 0;
        for (Node node : nodes) {
            Neuron[] neighbors = new Neuron[node.edges.size()];
            neurons[i] = new Neuron(node.w, neighbors);
            hash.put(node.id, neurons[i]);
            i++;
        }

        i = 0;
        for (Node node : nodes) {
            int j = 0;
            for (Edge edge : node.edges) {
                if (edge.a != node)
                    neurons[i].neighbors[j++] = hash.get(edge.a.id);
                else
                    neurons[i].neighbors[j++] = hash.get(edge.b.id);
            }
            i++;
        }
        
        return neurons;
    }
    
    /**
     * Update the Neural Gas with a new signal.
     */
    public void update(double[] x) {
        n++;

        if (nodes.size() < 2) {
            nodes.add(new Node(x.clone()));
            return;
        }

        // Find the nearest (s1) and second nearest (s2) neuron to x.
        Node[] top2 = new Node[2];
        HeapSelect<Node> heap = new HeapSelect<>(top2);
        for (Node neuron : nodes) {
            neuron.dist = MathEx.squaredDistance(neuron.w, x);
            heap.add(neuron);
        }

        Node s1 = top2[1];
        Node s2 = top2[0];

        // update s1
        s1.error += s1.dist;
        for (int i = 0; i < d; i++) {
            s1.w[i] += epsBest * (x[i] - s1.w[i]);
        }

        boolean addEdge = true;
        for (Edge edge : s1.edges) {
            // Update s1's direct topological neighbors twoards x.
            if (edge.a != s1) {
                for (int i = 0; i < d; i++) {
                    edge.a.w[i] += epsNeighbor * (x[i] - edge.a.w[i]);
                }
            } else {
                for (int i = 0; i < d; i++) {
                    edge.b.w[i] += epsNeighbor * (x[i] - edge.b.w[i]);
                }
            }

            // Increase the edge of all edges emanating from s1.
            edge.age++;

            // Set the age to zero if s1 and s2 are already connected.
            if (edge.a == s2 || edge.b == s2) {
                edge.age = 0;
                addEdge = false;
            }
        }

        // Connect s1 and s2 if they are not neighbor yet.
        if (addEdge) {
            Edge edge = new Edge(s1, s2);
            s1.edges.add(edge);
            s2.edges.add(edge);
        }

        // Remove edges with an age larger than the threshold
        for (Iterator<Edge> iter = s1.edges.iterator(); iter.hasNext();) {
            Edge edge = iter.next();
            if (edge.age > maxEdgeAge) {
                iter.remove();
                if (edge.a != s1) {
                    edge.a.edges.remove(edge);
                    // If it results in neuron having no emanating edges,
                    // remove the neuron as well.
                    if (edge.a.edges.isEmpty())
                        nodes.remove(edge.a);
                } else {
                    edge.b.edges.remove(edge);
                    if (edge.b.edges.isEmpty())
                        nodes.remove(edge.b);
                }
            }
        }

        // Add a new neuron if the number of input signals processed so far
        // is an integer multiple of lambda.
        if (n % lambda == 0) {
            // Determine the neuron qith the maximum accumulated error.
            Node q = nodes.get(0);
            for (Node neuron : nodes) {
                if (neuron.error > q.error)
                    q = neuron;
            }

            // Find the neighbor of q with the largest error variable.
            Node f = null;
            for (Edge edge : q.edges) {
                if (edge.a != q) {
                    if (f == null || edge.a.error > f.error)
                        f = edge.a;
                } else {
                    if (f == null || edge.b.error > f.error)
                        f = edge.b;
                }
            }

            // Insert a new neuron halfway between q and f.
            if (f != null) {
                double[] w = new double[d];
                for (int i = 0; i < d; i++) {
                    w[i] += (q.w[i] + f.w[i]) / 2;
                }
                Node r = new Node(w);

                // Decrease the error variables of q and f.
                q.error *= alpha;
                f.error *= alpha;

                // Initialize the error variable of new neuron with the one of q.
                r.error = q.error;
                nodes.add(r);
            }
        }

        // Decrease all error variables.
        for (Node neuron : nodes) {
            neuron.error *= beta;
        }
    }

    /**
     * Clustering neurons into k clusters.
     * @param k the number of clusters.
     */
    public void partition(int k) {
        double[][] x = new double[nodes.size()][];
        for (int i = 0; i < x.length; i++) {
            x[i] = nodes.get(i).w;
        }

        Linkage linkage = UPGMALinkage.of(x);
        HierarchicalClustering hc = HierarchicalClustering.fit(linkage);
        y = hc.partition(k);
    }

    /**
     * Cluster a new instance to the nearest neuron.
     * @param x a new instance.
     * @return the cluster label. If the method partition() was called,
     * this is the cluster id of nearest neuron. Otherwise, it is just
     * the index of neuron.
     */
    @Override
    public int predict(double[] x) {
        double minDist = Double.MAX_VALUE;
        int bestCluster = 0;

        int i = 0;
        for (Node neuron : nodes) {
            double dist = MathEx.squaredDistance(x, neuron.w);
            if (dist < minDist) {
                minDist = dist;
                bestCluster = i;
            }
            i++;
        }

        if (y == null || y.length != nodes.size()) {
            return bestCluster;
        } else {

            return y[bestCluster];
        }
    }
}
