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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import smile.clustering.Clustering;
import smile.clustering.HierarchicalClustering;
import smile.clustering.linkage.Linkage;
import smile.clustering.linkage.UPGMALinkage;
import smile.sort.HeapSelect;
import smile.math.Math;
import smile.stat.distribution.GaussianDistribution;

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
 * @see smile.clustering.BIRCH
 * 
 * @author Haifeng Li
 */
public class NeuralMap implements Clustering<double[]> {
    
    /**
     * The neurons in the network.
     */
    public static class Neuron {
        /**
         * The number of samples associated with this neuron.
         */
        public int n = 1;
        /**
         * The cluster label.
         */
        public int y = OUTLIER;
        /**
         * Reference vector.
         */
        public final double[] w;
        /**
         * Connected neighbors.
         */
        public final LinkedList<Neuron> neighbors = new LinkedList<>();

        /**
         * Constructor.
         * @param w the reference vector.
         */
        public Neuron(double[] w) {
            this.w = w;
        }
    }

    /**
     * The object encapsulates the results of nearest neighbor search.
     */
    class Neighbor implements Comparable<Neighbor> {

        /**
         * The neighbor neuron.
         */
        Neuron neuron;
        /**
         * The distance between the query and the neighbor.
         */
        double distance;

        /**
         * Constructor.
         * @param neuron the neighbor neuron.
         * @param distance the distance between the query and the neighbor.
         */
        Neighbor(Neuron neuron, double distance) {
            this.neuron = neuron;
            this.distance = distance;
        }

        @Override
        public int compareTo(Neighbor o) {
            return (int) Math.signum(distance - o.distance);
        }
    }

    /**
     * Locality-Sensitive Hashing (LSH) is an algorithm for solving the
     * (approximate/exact) Nearest Neighbor Search in high dimensional spaces
     * by performing probabilistic dimension reduction of data. The basic idea
     * is to hash the input items so that similar items are mapped to the same
     * buckets with high probability (the number of buckets being much smaller
     * than the universe of possible input items). This class implements a
     * space-efficient LSH algorithm in Euclidean spaces.
     */
    class LSH {

        /**
         * The hash function for data in Euclidean spaces.
         */
        class Hash {

            /**
             * The object in the hash table.
             */
            class Item {

                /**
                 * The bucket id given by the universal bucket hashing.
                 */
                int bucket;
                /**
                 * The neuron object.
                 */
                Neuron neuron;

                /**
                 * Constructor
                 */
                Item(int bucket, Neuron neuron) {
                    this.bucket = bucket;
                    this.neuron = neuron;
                }
            }
            /**
             * The random vectors with entries chosen independently from a Gaussian
             * distribution.
             */
            double[][] a;
            /**
             * Real numbers chosen uniformly from the range [0, w].
             */
            double[] b;
            /**
             * Hash table.
             */
            LinkedList<Item>[] table;

            /**
             * Constructor.
             */
            @SuppressWarnings("unchecked")
            Hash() {
                a = new double[k][d];
                b = new double[k];

                for (int i = 0; i < k; i++) {
                    for (int j = 0; j < d; j++) {
                        a[i][j] = GaussianDistribution.getInstance().rand();
                    }

                    b[i] = Math.random(0, w);
                }

                LinkedList<Item> list = new LinkedList<>();
                table = (LinkedList<Item>[]) java.lang.reflect.Array.newInstance(list.getClass(), H);
            }

            /**
             * Returns the raw hash value of given vector x.
             * @param x the vector to be hashed.
             * @param m the m-<i>th</i> hash function to be employed.
             * @return the raw hash value.
             */
            double hash(double[] x, int m) {
                double r = b[m];
                for (int j = 0; j < d; j++) {
                    r += a[m][j] * x[j];
                }
                return r / w;
            }

            /**
             * Apply hash functions on given vector x.
             * @param x the vector to be hashed.
             * @return the bucket of hash table for given vector x.
             */
            int hash(double[] x) {
                long r = 0;
                for (int i = 0; i < k; i++) {
                    double ri = hash(x, i);
                    r += c[i] * (int) Math.floor(ri);
                }

                int h = (int) (r % P);
                if (h < 0) {
                    h += P;
                }

                return h;
            }

            /**
             * Insert an item into the hash table.
             */
            void add(Neuron neuron) {
                int bucket = hash(neuron.w);
                int i = bucket % H;

                if (table[i] == null) {
                    table[i] = new LinkedList<>();
                }

                table[i].add(new Item(bucket, neuron));
            }
        }
        
        /**
         * Hash functions.
         */
        Hash[] hash;
        /**
         * The size of hash table.
         */
        int H;
        /**
         * The number of random projection hash functions.
         */
        int k;
        /**
         * The hash function is defined as floor((a * x + b) / w). The value
         * of w determines the bucket interval.
         */
        double w;
        /**
         * The random integer used for universal bucket hashing.
         */
        int[] c;
        /**
         * The prime number in universal bucket hashing.
         */
        int P = 2147483647;

        /**
         * Constructor.
         * @param L the number of hash tables.
         * @param k the number of random projection hash functions.
         * @param w the bucket interval.
         */
        LSH(int L, int k, double w) {
            this(L, k, w, 1017881);
        }

        /**
         * Constructor.
         * @param L the number of hash tables.
         * @param k the number of random projection hash functions.
         * @param w the bucket interval.
         * @param H the number of buckets of hash tables.
         */
        LSH(int L, int k, double w, int H) {
            this.k = k;
            this.w = w;
            this.H = H;

            hash = new Hash[L];

            c = new int[k];
            for (int i = 0; i < c.length; i++) {
                c[i] = Math.randomInt(P);
            }

            for (int i = 0; i < L; i++) {
                hash[i] = new Hash();
            }
        }

        /**
         * Insert a neuron to the hash table.
         */
        void add(Neuron neuron) {
            for (int i = 0; i < hash.length; i++) {
                hash[i].add(neuron);
            }
        }

        /**
         * Remove a neuron to the hash table.
         */
        void remove(Neuron neuron) {
            for (int i = 0; i < hash.length; i++) {
                int bucket = hash[i].hash(neuron.w);

                LinkedList<Hash.Item> bin = hash[i].table[bucket % H];
                if (bin != null) {
                    for (Hash.Item e : bin) {
                        if (e.bucket == bucket && e.neuron == neuron) {
                            bin.remove(e);
                            break;
                        }
                    }
                }
            }
        }

        /**
         * Returns the nearest neighbor of x.
         */
        Neighbor nearest(double[] x) {
            Neighbor neighbor = new Neighbor(null, Double.MAX_VALUE);

            for (int i = 0; i < hash.length; i++) {
                int bucket = hash[i].hash(x);
                LinkedList<Hash.Item> bin = hash[i].table[bucket % H];
                if (bin != null) {
                    for (Hash.Item e : bin) {
                        if (e.bucket == bucket) {
                            double distance = Math.distance(x, e.neuron.w);
                            if (distance < neighbor.distance) {
                                neighbor.distance = distance;
                                neighbor.neuron = e.neuron;
                            }
                        }
                    }
                }
            }

            return neighbor;
        }

        /**
         * Returns the k-nearest neighbors of x.
         */
        int knn(double[] x, Neighbor[] neighbors) {
            int hit = 0;
            HeapSelect<Neighbor> heap = new HeapSelect<>(neighbors);

            for (int i = 0; i < hash.length; i++) {
                int bucket = hash[i].hash(x);
                LinkedList<Hash.Item> bin = hash[i].table[bucket % H];
                if (bin != null) {
                    for (Hash.Item e : bin) {
                        if (e.bucket == bucket) {
                            boolean existed = false;
                            for (Neighbor n : neighbors) {
                                if (n != null && e.neuron == n.neuron) {
                                    existed = true;
                                    break;
                                }
                            }

                            if (!existed) {
                                //hit++;
                                double distance = Math.distance(x, e.neuron.w);
                                if (heap.peek() == null || distance < heap.peek().distance) {
                                    heap.add(new Neighbor(e.neuron, distance));
                                    hit++;
                                }
                            }
                        }
                    }
                }
            }

            return hit;
        }
    }
    
    /**
     * The dimensionality of signals.
     */
    private int d;
    /**
     * The distance radius to activate a neuron for a given signal.
     */
    private double r;
    /**
     * The fraction to update nearest neuron.
     */
    private double epsBest = 0.05;
    /**
     * The fraction to update neighbors of nearest neuron.
     */
    private double epsNeighbor = 0.0006;
    /**
     * Neurons in the neural network.
     */
    private LSH lsh;
    /**
     * The list of neurons.
     */
    private List<Neuron> neurons = new ArrayList<>();

    /**
     * Constructor.
     * @param d the dimensionality of signals.
     * @param r the distance radius to activate a neuron for a given signal.
     * @param epsBest the fraction to update activated neuron.
     * @param epsNeighbor the fraction to update neighbors of activated neuron.
     * @param L the number of hash tables.
     * @param k the number of random projection hash functions.
     */
    public NeuralMap(int d, double r, double epsBest, double epsNeighbor, int L, int k) {
        this.d = d;
        this.r = r;
        this.epsBest = epsBest;
        this.epsNeighbor = epsNeighbor;
        lsh = new LSH(L, k, 4 * r);
    }

    /**
     * Update the network with a new signal.
     */
    public void update(double[] x) {
        // Find the nearest (s1) and second nearest (s2) neuron to x.
        Neighbor[] top2 = new Neighbor[2];
        int k = lsh.knn(x, top2);

        double dist = Double.MAX_VALUE;
        Neuron neuron = null;
        if (k == 0) {
            neuron = new Neuron(x.clone());
            lsh.add(neuron);
            neurons.add(neuron);
            return;
        } else if (k == 1) {
            dist = top2[0].distance;

            if (dist <= r) {
                neuron = top2[0].neuron;
                neuron.n++;
                lsh.remove(neuron);
                for (int i = 0; i < d; i++) {
                    neuron.w[i] += epsBest * (x[i] - neuron.w[i]);
                }
                lsh.add(neuron);

            } else {
                neuron = new Neuron(x.clone());
                lsh.add(neuron);
                neurons.add(neuron);

                Neuron second = top2[0].neuron;
                neuron.neighbors.add(second);
                second.neighbors.add(neuron);
            }
        } else {
            dist = top2[1].distance;
            if (dist <= r) {
                neuron = top2[1].neuron;
                lsh.remove(neuron);
                for (int i = 0; i < d; i++) {
                    neuron.w[i] += epsBest * (x[i] - neuron.w[i]);
                }
                lsh.add(neuron);

                Neuron second = top2[0].neuron;
                second.n++;
                boolean connected = false;
                for (Neuron neighbor : neuron.neighbors) {
                    if (neighbor == second) {
                        connected = true;
                        break;
                    }
                }

                if (!connected) {
                    neuron.neighbors.add(second);
                    second.neighbors.add(neuron);
                }
            } else {
                neuron = new Neuron(x.clone());
                lsh.add(neuron);
                neurons.add(neuron);

                Neuron second = top2[1].neuron;
                neuron.neighbors.add(second);
                second.neighbors.add(neuron);
            }
        }

        // update the neighbors of activated neuron.
        for (Iterator<Neuron> iter = neuron.neighbors.iterator(); iter.hasNext(); ) {
            Neuron neighbor = iter.next();
            lsh.remove(neighbor);
            for (int i = 0; i < d; i++) {
                neighbor.w[i] += epsNeighbor * (x[i] - neighbor.w[i]);
            }

            if (Math.distance(neuron.w, neighbor.w) > 2 * r) {
                neighbor.neighbors.remove(neuron);
                iter.remove();
            }

            if (!neighbor.neighbors.isEmpty()) {
                lsh.add(neighbor);
            } else {
                neurons.remove(neighbor);
            }
        }

        if (neuron.neighbors.isEmpty()) {
            lsh.remove(neuron);
            neurons.remove(neuron);
        }
    }

    /**
     * Returns the set of neurons.
     */
    public List<Neuron> neurons() {
        return neurons;
    }
    
    /**
     * Removes neurons with the number of samples less than a given threshold.
     * The neurons without neighbors will also be removed.
     * @param minPts neurons will be removed if the number of its points is
     * less than minPts.
     * @return the number of neurons after purging.
     */
    public int purge(int minPts) {
        List<Neuron> outliers = new ArrayList<>();
        for (Neuron neuron : neurons) {
            if (neuron.n < minPts) {
                outliers.add(neuron);
            }
        }
        
        neurons.removeAll(outliers);
        for (Neuron neuron : neurons) {
            neuron.neighbors.removeAll(outliers);
        }
        
        outliers.clear();
        for (Neuron neuron : neurons) {
            if (neuron.neighbors.isEmpty()) {
                outliers.add(neuron);
            }
        }
        
        neurons.removeAll(outliers);
        return neurons.size();
    }

    /**
     * Clustering neurons into k clusters.
     * @param k the number of clusters.
     */
    public void partition(int k) {
        partition(k, 0);
    }
    
    /**
     * Clustering neurons into k clusters.
     * @param k the number of clusters.
     * @param minPts a neuron will be treated as outlier if the number of its
     * points is less than minPts.
     * @return the number of non-outlier leaves.
     */
    public int partition(int k, int minPts) {
        List<Neuron> data = new ArrayList<>();
        for (Neuron neuron : neurons) {
            neuron.y = OUTLIER;
            if (neuron.n >= minPts) {
                data.add(neuron);
            }
        }
        
        double[][] proximity = new double[data.size()][];
        for (int i = 0; i < data.size(); i++) {
            proximity[i] = new double[i + 1];
            for (int j = 0; j < i; j++) {
                proximity[i][j] = Math.distance(data.get(i).w, data.get(j).w);
            }
        }

        Linkage linkage = new UPGMALinkage(proximity);
        HierarchicalClustering hc = new HierarchicalClustering(linkage);
        int[] y = hc.partition(k);

        for (int i = 0; i < data.size(); i++) {
            data.get(i).y = y[i];
        }
        
        return data.size();
    }

    /**
     * Cluster a new instance to the nearest neuron. The method partition()
     * should be called first.
     * @param x a new instance.
     * @return the cluster label of nearest neuron.
     */
    @Override
    public int predict(double[] x) {
        double minDist = Double.MAX_VALUE;
        int bestCluster = 0;

        for (Neuron neuron : neurons) {
            double dist = Math.squaredDistance(x, neuron.w);
            if (dist < minDist) {
                minDist = dist;
                bestCluster = neuron.y;
            }
        }

        return bestCluster;
    }
}
