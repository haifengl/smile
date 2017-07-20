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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.clustering.Clustering;
import smile.clustering.HierarchicalClustering;
import smile.clustering.BBDTree;
import smile.clustering.linkage.Linkage;
import smile.clustering.linkage.UPGMALinkage;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;

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
public class SOM implements Clustering<double[]> {
    private static final Logger logger = LoggerFactory.getLogger(SOM.class);

    /**
     * Self-Organizing Map Neuron.
     */
    public static class Neuron {
        /**
         * Weight vector.
         */
        public double[] w;
        /**
         * Cluster id of this neuron.
         */
        public int cluster;
        /**
         * The samples that are best matched.
         */
        public int[] samples;
        /**
         * The class label of majority, y = which.max(ni).
         */
        public int y;
        /**
         * The count of each class that best matched samples belong to.
         */
        public int[] ni;
        /**
         * The distance to neighbors.
         */
        public double[] distance;
    }
    
    /**
     * The width of map.
     */
    private int width;
    /**
     * The height of map.
     */
    private int height;
    /**
     * The dimension of input space.
     */
    private int d;
    /**
     * The SOM map of neurons.
     */
    private double[][][] neurons;
    /**
     * The best matched unit. This is n-by-2 matrix, of which each row is
     * for each data point. The entry bmu[i][0] and bmu[i][1] are the row
     * index and column index of the best matched unit for each sample,
     * respectively.
     */
    private int[][] bmu;
    /**
     * The number of samples in each units.
     */
    private int[][] voronoi;
    /**
     * The cluster labels of neurons.
     */
    private int[] y;
    /**
     * Unified distance matrix, or u-matrix, is a popular method of displaying
     * SOMs. The value of umatrix is the maximum of distances between a map unit
     * to its neighbors.
     */
    private double[][] umatrix;

    /**
     * Constructor. Learn the SOM of given data.
     * @param size the size of a squared map.
     */
    public SOM(double[][] data, int size) {
        this(data, size, size);
    }

    /**
     * Constructor. Learn the SOM of given data.
     * @param width the width of map.
     * @param height the height of map.
     */
    public SOM(double[][] data, int width, int height) {
        if (height <= 0 || width <= 0 || height * width == 1) {
            throw new IllegalArgumentException("Invalide map width = " + width + " or height = " + height);
        }

        this.width = width;
        this.height = height;
        this.d = data[0].length;
        
        int n = data.length;

        neurons = new double[height][width][d];
        bmu = new int[n][2];

        double mpd = Math.max(0.5, height * width / (double) n);
        int roughTrainLen = (int) Math.ceil(10 * mpd);
        int fineTrainLen = (int) Math.ceil(40 * mpd);
        int trainLen = roughTrainLen + fineTrainLen;

        double initRadius = Math.max(1.0, Math.max(height, width) / 2.0);
        double finalRadius = Math.max(1.0, initRadius / 4.0);

        double[] radius = new double[trainLen];
        for (int i = 0; i < roughTrainLen; i++) {
            radius[roughTrainLen - i - 1] = finalRadius + (double) i / (roughTrainLen - 1) * (initRadius - finalRadius);
        }

        initRadius = finalRadius - 1.0 / (fineTrainLen - 1);
        finalRadius = 1;
        for (int i = 0; i < fineTrainLen; i++) {
            radius[roughTrainLen + fineTrainLen - i - 1] = finalRadius + (double) i / (fineTrainLen - 1) * (initRadius - finalRadius);
        }

        for (int i = 0; i < trainLen; i++) {
            radius[i] = radius[i] * radius[i];
        }

        double[] mu = new double[d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                mu[j] += data[i][j];
            }
        }

        for (int i = 0; i < d; i++) {
            mu[i] /= n;
        }

        DenseMatrix D = Matrix.zeros(n, d);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                D.set(i, j, data[i][j] - mu[j]);
            }
        }

        DenseMatrix V = Matrix.zeros(d, d);
        for (int i = 0; i < d; i++) {
            for (int j = i; j < d; j++) {
                for (int k = 0; k < n; k++) {
                    V.add(i, j, D.get(k, i) * D.get(k, j));
                }
                V.div(i, j, n);
                V.set(j, i, V.get(i, j));
            }
        }

        V.setSymmetric(true);
        EVD eigen = V.eigen(2);

        double[] v1 = new double[d];
        double[] v2 = new double[d];
        for (int i = 0; i < d; i++) {
            v1[i] = eigen.getEigenVectors().get(i, 0);
            v2[i] = eigen.getEigenVectors().get(i, 1);
        }

        for (int i = 0; i < height; i++) {
            double w = (double) i / height - 0.5;
            for (int j = 0; j < width; j++) {
                double h = (double) j / width - 0.5;
                for (int k = 0; k < d; k++) {
                    neurons[i][j][k] = mu[k] + w * v1[k] + h * v2[k];
                }
            }
        }
        
        BBDTree bbd = new BBDTree(data);
        double[][] centroids = new double[width * height][];
        double[][] sums = new double[width * height][d];
        int[] hit = new int[width * height];
        int[] sy = new int[n]; // cluster label of samples.

        for (int i = 0, k = 0; i < height; i++) {
            for (int j = 0; j < width; j++, k++) {
                centroids[k] = neurons[i][j];
            }
        }

        for (int iter = 0; iter < trainLen; iter++) {
            int r = (int) Math.round(Math.sqrt(radius[iter]));
            double distortion = bbd.clustering(centroids, sums, hit, sy);
            logger.info(String.format("SOM distortion after %3d iterations (radius = %d): %.5f", iter + 1, r, distortion));

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    double denom = 0.0;
                    Arrays.fill(neurons[i][j], 0.0);

                    int minH = Math.max(0, i - 3 * r) + 1;
                    int maxH = Math.min(height, i + 3 * r) - 1;
                    int minW = Math.max(0, j - 3 * r) + 1;
                    int maxW = Math.min(width, j + 3 * r) - 1;

                    int hits = 0;
                    while (hits == 0) {
                        minH = Math.max(0, minH - 1);
                        maxH = Math.min(height, maxH + 1);
                        minW = Math.max(0, minW - 1);
                        maxW = Math.min(width, maxW + 1);

                        for (int s = minH; s < maxH; s++) {
                            for (int t = minW; t < maxW; t++) {
                                int pos = s * width + t;
                                hits += hit[pos];
                            }
                        }
                    }

                    for (int s = minH; s < maxH; s++) {
                        for (int t = minW; t < maxW; t++) {
                            int pos = s * width + t;
                            if (hit[pos] > 0) {
                                int dx = i - s;
                                int dy = j - t;
                                double h = Math.exp(-(dx * dx + dy * dy) / (2 * radius[iter]));
                                denom += h * hit[pos];
                                for (int k = 0; k < d; k++) {
                                    neurons[i][j][k] += h * sums[pos][k];
                                }
                            }
                        }
                    }

                    for (int k = 0; k < d; k++) {
                        neurons[i][j][k] /= denom;
                    }
                }
            }
        }

        // best matched unit for each sample.
        for (int i = 0; i < n; i++) {
            bmu[i][0] = sy[i] / width;
            bmu[i][1] = sy[i] % width;
        }
        
        // count the number of samples in each unit.
        voronoi = new int[height][width];
        voronoi = new int[height][width];
        for (int i = 0; i < bmu.length; i++) {
            voronoi[bmu[i][0]][bmu[i][1]]++;
        }
        
        // calculate u-matrix.
        umatrix = new double[height][width];
        for (int i = 0; i < height - 1; i++) {
            for (int j = 0; j < width - 1; j++) {
                double dist = Math.sqrt(Math.squaredDistance(neurons[i][j], neurons[i][j + 1]));
                umatrix[i][j] = Math.max(umatrix[i][j], dist);
                umatrix[i][j + 1] = Math.max(umatrix[i][j + 1], dist);
                dist = Math.sqrt(Math.squaredDistance(neurons[i][j], neurons[i + 1][j]));
                umatrix[i][j] = Math.max(umatrix[i][j], dist);
                umatrix[i + 1][j] = Math.max(umatrix[i + 1][j], dist);
            }
        }

        for (int i = 0; i < height - 1; i++) {
            double dist = Math.sqrt(Math.squaredDistance(neurons[i][width - 1], neurons[i + 1][width - 1]));
            umatrix[i][width - 1] = Math.max(umatrix[i][width - 1], dist);
            umatrix[i + 1][width - 1] = Math.max(umatrix[i + 1][width - 1], dist);
        }

        for (int j = 0; j < width - 1; j++) {
            double dist = Math.sqrt(Math.squaredDistance(neurons[height - 1][j], neurons[height - 1][j + 1]));
            umatrix[height - 1][j] = Math.max(umatrix[height - 1][j], dist);
            umatrix[height - 1][j + 1] = Math.max(umatrix[height - 1][j + 1], dist);
        }

        umatrix[height - 1][width - 1] = Math.max(umatrix[height - 1][width - 2], umatrix[height - 2][width - 1]);
    }

    /**
     * Returns the SOM map grid.
     */
    public double[][][] map() {
        return neurons;
    }

    /**
     * Returns the U-Matrix of SOM map for visualization.
     */
    public double[][] umatrix() {
        return umatrix;
    }

    /**
     * Returns the best matched unit for each sample.
     * @return the best matched unit. This is n-by-2 matrix, of which each row
     * is for each data point. The entry bmu[i][0] and bmu[i][1] are the row
     * index and column index of the best matched unit for each sample,
     * respectively.
     */
    public int[][] bmu() {
        return bmu;
    }
    
    /**
     * Returns the number of samples in each unit.
     */
    public int[][] size() {
        return voronoi;
    }

    /**
     * Returns the cluster labels for each neuron.  If the neurons have
     * not been clustered, throws an Illegal State Exception.
     */
    public int[][] getClusterLabel() {
        if (y == null) {
           throw new IllegalStateException("Neuron cluster labels are not available. Call partition() first.");
        }

        int[][] clusterLabels = new int[height][width];
        for (int i = 0, l = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                clusterLabels[i][j] = y[i*width + j];
            }
        }
        return clusterLabels;
    }

    /**
     * Clustering the neurons into k groups. And then assigns the samples in
     * each neuron to the corresponding cluster.
     * @param k the number of clusters.
     * @return the cluster label of samples.
     */
    public int[] partition(int k) {
        int n = width * height;
        double[][] units = new double[n][d];
        for (int i = 0, l = 0; i < height; i++) {
            for (int j = 0; j < width; j++, l++) {
                units[l] = neurons[i][j];
            }
        }

        double[][] proximity = new double[n][];
        for (int i = 0; i < n; i++) {
            proximity[i] = new double[i + 1];
            for (int j = 0; j < i; j++) {
                proximity[i][j] = Math.distance(units[i], units[j]);
            }
        }

        Linkage linkage = new UPGMALinkage(proximity);
        HierarchicalClustering hc = new HierarchicalClustering(linkage);
        y = hc.partition(k);

        int[] cluster = new int[bmu.length];
        for (int i = 0; i < cluster.length; i++) {
            cluster[i] = y[bmu[i][0] * width + bmu[i][1]];
        }
        
        return cluster;
    }

    /**
     * Cluster a new instance to the nearest neuron. For clustering purpose,
     * one should build a sufficient large map to capture the structure of
     * data space. Then the neurons of map can be clustered into a small number
     * of clusters. Finally the sample should be assign to the cluster of
     * its nearest neurons.
     * 
     * @param x a new instance.
     * @return the cluster label. If the method {@link #partition(int)} is
     * called before, this is the cluster label of the nearest neuron.
     * Otherwise, this is the index of neuron (i * width + j).
     */
    @Override
    public int predict(double[] x) {
        double best = Double.MAX_VALUE;
        int ii = -1, jj = -1;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                double dist = Math.squaredDistance(neurons[i][j], x);
                if (dist < best) {
                    best = dist;
                    ii = i;
                    jj = j;
                }
            }
        }
        
        if (y == null) {
            return ii * width + jj;
        } else {
            return y[ii * width + jj];
        }
    }
}
