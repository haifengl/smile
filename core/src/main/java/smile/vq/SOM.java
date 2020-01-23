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
import smile.math.MathEx;
import smile.mds.MDS;
import smile.sort.QuickSort;
import smile.math.TimeFunction;

/**
 * Self-Organizing Map. An SOM is a unsupervised learning method to produce
 * a low-dimensional (typically two-dimensional) discretized representation
 * (called a map) of the input space of the training samples. The model was
 * first described as an artificial neural network by Teuvo Kohonen, and is
 * sometimes called a Kohonen map.
 * <p>
 * While it is typical to consider SOMs as related to feed-forward networks where
 * the nodes are visualized as being attached, this type of architecture is
 * fundamentally different in arrangement and motivation because SOMs use a
 * neighborhood function to preserve the topological properties of the input
 * space. This makes SOMs useful for visualizing low-dimensional views of
 * high-dimensional data, akin to multidimensional scaling.
 * <p>
 * SOMs belong to a large family of competitive learning process and vector
 * quantization. An SOM consists of components called nodes or neurons.
 * Associated with each node is a weight vector of the same dimension as
 * the input data vectors and a position in the map space. The usual arrangement
 * of nodes is a regular spacing in a hexagonal or rectangular grid. The
 * self-organizing map describes a mapping from a higher dimensional input
 * space to a lower dimensional map space. During the (iterative) learning,
 * the input vectors are compared to the weight vector of each neuron. Neurons
 * who most closely match the input are known as the best match unit (BMU) of
 * the system. The weight vector of the BMU and those of nearby neurons are
 * adjusted to be closer to the input vector by a certain step size.
 * <p>
 * There are two ways to interpret a SOM. Because in the training phase weights
 * of the whole neighborhood are moved in the same direction, similar items
 * tend to excite adjacent neurons. Therefore, SOM forms a semantic map where
 * similar samples are mapped close together and dissimilar apart.
 * The other way is to think of neuronal weights as pointers to the input space.
 * They form a discrete approximation of the distribution of training samples.
 * More neurons point to regions with high training sample concentration and
 * fewer where the samples are scarce.
 * <p>
 * SOM may be considered a nonlinear generalization of Principal components
 * analysis (PCA). It has been shown, using both artificial and real
 * geophysical data, that SOM has many advantages over the conventional feature
 * extraction methods such as Empirical Orthogonal Functions (EOF) or PCA.
 * <p>
 * It has been shown that while SOMs with a small number of nodes behave in a
 * way that is similar to K-means. However, larger SOMs rearrange data
 * in a way that is fundamentally topological in character and display properties
 * which are emergent. Therefore, large maps are preferable to smaller ones.
 * In maps consisting of thousands of nodes, it is possible to perform cluster
 * operations on the map itself.
 * <p>
 * A common way to display SOMs is the heat map of U-matrix. The U-matrix value
 * of a particular node is the minimum/maximum/average distance between the node
 * and its closest neighbors. In a rectangular grid for instance, we might
 * consider the closest 4 or 8 nodes.
 *
 * <h2>References</h2>
 * <ol>
 * <li> Teuvo KohonenDan. Self-organizing maps. Springer, 3rd edition, 2000. </li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class SOM implements VectorQuantizer {
    private static final long serialVersionUID = 2L;

    /**
     * Self-Organizing Map Neuron.
     */
    private static class Neuron implements Serializable {
        /** The weight vector. */
        public final double[] w;
        /** The row index of neuron in the lattice. */
        public final int i;
        /** The column index of neuron in the lattice. */
        public final int j;

        /**
         * Constructor.
         * @param i the row index of neuron in the lattice.
         * @param j the column index of neuron in the lattice.
         * @param w the weight vector.
         */
        public Neuron(int i, int j, double[] w) {
            this.i = i;
            this.j = j;
            this.w = w;
        }
    }
    
    /**
     * The number of rows in the lattice.
     */
    private int nrows;
    /**
     * The number of columns in the lattice.
     */
    private int ncols;
    /**
     * The lattice of neurons.
     */
    private Neuron[][] map;
    /**
     * The neurons in linear array.
     */
    private Neuron[] neurons;
    /**
     * The distance between a new observation to neurons.
     */
    private double[] dist;
    /**
     * The learning rate function.
     */
    private TimeFunction alpha;
    /**
     * The neighborhood function.
     */
    private Neighborhood theta;
    /**
     * The current iteration.
     */
    private int t = 0;
    /**
     * The threshold to update neuron if alpha * theta > eps.
     */
    private double eps = 1E-5;

    /**
     * Constructor.
     * @param neurons the initial lattice of neurons.
     * @param alpha the learning rate function.
     * @param theta the neighborhood function.
     */
    public SOM(double[][][] neurons, TimeFunction alpha, Neighborhood theta) {
        this.alpha = alpha;
        this.theta = theta;
        this.nrows = neurons.length;
        this.ncols = neurons[0].length;
        
        this.map = new Neuron[nrows][ncols];
        this.neurons = new Neuron[nrows * ncols];
        this.dist = new double[this.neurons.length];

        for (int i = 0, k = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++, k++) {
                Neuron neuron = new Neuron(i, j, neurons[i][j].clone());
                map[i][j] = neuron;
                this.neurons[k] = neuron;
            }
        }
    }

    /**
     * Creates a lattice of which the weight vectors are randomly selected from samples.
     * @param nrows the number of rows in the lattice.
     * @param ncols the number of columns in the lattice.
     * @param samples the samples to draw initial weight vectors.
     */
    public static double[][][] lattice(int nrows, int ncols, double[][] samples) {
        int k = nrows * ncols;
        int n = samples.length;

        int[] clusters = new int[n];
        double[][] medoids = new double[k][];
        CentroidClustering.seed(samples, medoids, clusters, MathEx::squaredDistance);

        // Pair-wise distance matrix.
        double[][] pdist = MathEx.pdist(medoids);
        MDS mds = MDS.of(pdist);
        double[][] coordinates = mds.coordinates;

        double[] x = Arrays.stream(coordinates).mapToDouble(point -> point[0]).toArray();
        double[] y = new double[ncols];
        int[] row = new int[ncols];
        int[] index = QuickSort.sort(x);

        double[][][] neurons = new double[nrows][ncols][];
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                int point = index[i * ncols + j];
                y[j] = coordinates[point][1];
                row[j] = point;
            }

            QuickSort.sort(y, row);

            for (int j = 0; j < ncols; j++) {
                neurons[i][j] = medoids[row[j]];
            }
        }

        return neurons;
    }

    @Override
    public void update(double[] x) {
        Neuron bmu = bmu(x);
        int i = bmu.i;
        int j = bmu.j;

        int d = bmu.w.length;
        double rate = alpha.of(t);
        Arrays.stream(neurons).parallel().forEach(neuron -> {
            double delta = rate * theta.of(neuron.i - i, neuron.j - j, t);
            if (delta > eps) {
                double[] w = neuron.w;
                for (int k = 0; k < d; k++) {
                    w[k] += delta * (x[k] - w[k]);
                }
            }
        });

        t = t + 1;
    }

    /**
     * Returns the lattice of neurons.
     */
    public double[][][] neurons() {
        double[][][] lattice = new double[nrows][ncols][];
        for (int i = 0; i < nrows; i++) {
            for (int j = 0; j < ncols; j++) {
                lattice[i][j] = map[i][j].w;
            }
        }
        return lattice;
    }

    /**
     * Calculates the unified distance matrix (u-matrix) for visualization.
     * U-matrix is a popular method of displaying SOMs. The value of umatrix
     * is the maximum of distances between a map unit to its neighbors.
     */
    public double[][] umatrix() {
        double[][] umatrix = new double[nrows][ncols];
        for (int i = 0; i < nrows - 1; i++) {
            for (int j = 0; j < ncols - 1; j++) {
                double dist = Math.sqrt(MathEx.distance(map[i][j].w, map[i][j + 1].w));
                umatrix[i][j] = Math.max(umatrix[i][j], dist);
                umatrix[i][j + 1] = Math.max(umatrix[i][j + 1], dist);

                dist = Math.sqrt(MathEx.distance(map[i][j].w, map[i + 1][j].w));
                umatrix[i][j] = Math.max(umatrix[i][j], dist);
                umatrix[i + 1][j] = Math.max(umatrix[i + 1][j], dist);
            }
        }

        for (int i = 0; i < nrows - 1; i++) {
            double dist = Math.sqrt(MathEx.distance(map[i][ncols - 1].w, map[i + 1][ncols - 1].w));
            umatrix[i][ncols - 1] = Math.max(umatrix[i][ncols - 1], dist);
            umatrix[i + 1][ncols - 1] = Math.max(umatrix[i + 1][ncols - 1], dist);
        }

        for (int j = 0; j < ncols - 1; j++) {
            double dist = Math.sqrt(MathEx.distance(map[nrows - 1][j].w, map[nrows - 1][j + 1].w));
            umatrix[nrows - 1][j] = Math.max(umatrix[nrows - 1][j], dist);
            umatrix[nrows - 1][j + 1] = Math.max(umatrix[nrows - 1][j + 1], dist);
        }

        umatrix[nrows - 1][ncols - 1] = Math.max(umatrix[nrows - 1][ncols - 2], umatrix[nrows - 2][ncols - 1]);

        return umatrix;
    }

    @Override
    public double[] quantize(double[] x) {
        return bmu(x).w;
    }

    /** Returns the best matching unit. */
    private Neuron bmu(double[] x) {
        IntStream.range(0, neurons.length).parallel().forEach(i -> dist[i] = MathEx.distance(neurons[i].w, x));
        QuickSort.sort(dist, neurons);
        return neurons[0];
    }
}
