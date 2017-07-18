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

package smile.classification;

import java.io.Serializable;
import java.util.Arrays;

import smile.math.Math;
import smile.math.distance.Metric;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.QR;
import smile.math.rbf.GaussianRadialBasis;
import smile.math.rbf.RadialBasisFunction;
import smile.util.SmileUtils;

/**
 * Radial basis function networks. A radial basis function network is an
 * artificial neural network that uses radial basis functions as activation
 * functions. It is a linear combination of radial basis functions. They are
 * used in function approximation, time series prediction, and control.
 * <p>
 * In its basic form, radial basis function network is in the form
 * <p>
 * y(x) = &Sigma; w<sub>i</sub> &phi;(||x-c<sub>i</sub>||)
 * <p>
 * where the approximating function y(x) is represented as a sum of N radial
 * basis functions &phi;, each associated with a different center c<sub>i</sub>,
 * and weighted by an appropriate coefficient w<sub>i</sub>. For distance,
 * one usually chooses Euclidean distance. The weights w<sub>i</sub> can
 * be estimated using the matrix methods of linear least squares, because
 * the approximating function is linear in the weights.
 * <p>
 * The centers c<sub>i</sub> can be randomly selected from training data,
 * or learned by some clustering method (e.g. k-means), or learned together
 * with weight parameters undergo a supervised learning processing
 * (e.g. error-correction learning).
 * <p>
 * The popular choices for &phi; comprise the Gaussian function and the so
 * called thin plate splines. The advantage of the thin plate splines is that
 * their conditioning is invariant under scalings. Gaussian, multi-quadric
 * and inverse multi-quadric are infinitely smooth and and involve a scale
 * or shape parameter, r<sub><small>0</small></sub> &gt; 0. Decreasing
 * r<sub><small>0</small></sub> tends to flatten the basis function. For a
 * given function, the quality of approximation may strongly depend on this
 * parameter. In particular, increasing r<sub><small>0</small></sub> has the
 * effect of better conditioning (the separation distance of the scaled points
 * increases).
 * <p>
 * A variant on RBF networks is normalized radial basis function (NRBF)
 * networks, in which we require the sum of the basis functions to be unity.
 * NRBF arises more naturally from a Bayesian statistical perspective. However,
 * there is no evidence that either the NRBF method is consistently superior
 * to the RBF method, or vice versa.
 * <p>
 * SVMs with Gaussian kernel have similar structure as RBF networks with
 * Gaussian radial basis functions. However, the SVM approach "automatically"
 * solves the network complexity problem since the size of the hidden layer
 * is obtained as the result of the QP procedure. Hidden neurons and
 * support vectors correspond to each other, so the center problems of
 * the RBF network is also solved, as the support vectors serve as the
 * basis function centers. It was reported that with similar number of support
 * vectors/centers, SVM shows better generalization performance than RBF
 * network when the training data size is relatively small. On the other hand,
 * RBF network gives better generalization performance than SVM on large
 * training data.
 * <p>
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Simon Haykin. Neural Networks: A Comprehensive Foundation (2nd edition). 1999. </li> 
 * <li> T. Poggio and F. Girosi. Networks for approximation and learning. Proc. IEEE 78(9):1484-1487, 1990. </li>
 * <li> Nabil Benoudjit and Michel Verleysen. On the kernel widths in radial-basis function networks. Neural Process, 2003.</li>
 * </ol>
 * 
 * @see RadialBasisFunction
 * @see SVM
 * @see NeuralNetwork
 * 
 * @author Haifeng Li
 */
public class RBFNetwork<T> implements Classifier<T>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The number of classes.
     */
    private int k;
    /**
     * The centers of RBF functions.
     */
    private T[] centers;
    /**
     * The linear weights.
     */
    private DenseMatrix w;
    /**
     * The distance metric functor.
     */
    private Metric<T> distance;
    /**
     * The radial basis function.
     */
    private RadialBasisFunction[] rbf;
    /**
     * True to fit a normalized RBF network.
     */
    private boolean normalized;

    /**
     * Trainer for RBF networks.
     */
    public static class Trainer<T> extends ClassifierTrainer<T> {
        /**
         * The number of radial basis functions.
         */
        private int m = 10;
        /**
         * The distance metric functor.
         */
        private Metric<T> distance;
        /**
         * The radial basis functions.
         */
        private RadialBasisFunction[] rbf;
        /**
         * True to fit a normalized RBF network.
         */
        private boolean normalized = false;

        /**
         * Constructor.
         * 
         * @param distance the distance metric functor.
         */
        public Trainer(Metric<T> distance) {
            this.distance = distance;
        }
        
        /**
         * Sets the radial basis function.
         * @param rbf the radial basis function.
         * @param m the number of basis functions.
         */
        public Trainer<T> setRBF(RadialBasisFunction rbf, int m) {
            this.m = m;
            this.rbf = rep(rbf, m);
            return this;
        }
        
        /**
         * Sets the radial basis functions.
         * @param rbf the radial basis functions.
         */
        public Trainer<T> setRBF(RadialBasisFunction[] rbf) {
            this.m = rbf.length;
            this.rbf = rbf;
            return this;
        }
        
        /**
         * Sets true to learn normalized RBF network.
         * @param normalized true to learn normalized RBF network.
         */
        public Trainer<T> setNormalized(boolean normalized) {
            this.normalized = normalized;
            return this;
        }
        
        @Override
        public RBFNetwork<T> train(T[] x, int[] y) {         
            @SuppressWarnings("unchecked")
            T[] centers = (T[]) java.lang.reflect.Array.newInstance(x.getClass().getComponentType(), m);
            GaussianRadialBasis gaussian = SmileUtils.learnGaussianRadialBasis(x, centers, distance);
            
            if (rbf == null) {
                return new RBFNetwork<>(x, y, distance, gaussian, centers, normalized);
            } else {
                return new RBFNetwork<>(x, y, distance, rbf, centers, normalized);
            }
        }
        
        /**
         * Learns a RBF network with given centers.
         * 
         * @param x training samples.
         * @param y training labels in [0, k), where k is the number of classes.
         * @param centers the centers of RBF functions.
         * @return a trained RBF network
         */
        public RBFNetwork<T> train(T[] x, int[] y, T[] centers) {
            return new RBFNetwork<>(x, y, distance, rbf, centers, normalized);
        }
    }
    
    /**
     * Constructor. Learn a regular RBF network without normalization.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param distance the distance metric functor.
     * @param rbf the radial basis function.
     * @param centers the centers of RBF functions.
     */
    public RBFNetwork(T[] x, int[] y, Metric<T> distance, RadialBasisFunction rbf, T[] centers) {
        this(x, y, distance, rbf, centers, false);
    }

    /**
     * Constructor. Learn a regular RBF network without normalization.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param distance the distance metric functor.
     * @param rbf the radial basis function.
     * @param centers the centers of RBF functions.
     */
    public RBFNetwork(T[] x, int[] y, Metric<T> distance, RadialBasisFunction[] rbf, T[] centers) {
        this(x, y, distance, rbf, centers, false);
    }

    /**
     * Constructor. Learn a RBF network.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param distance the distance metric functor.
     * @param rbf the radial basis functions.
     * @param centers the centers of RBF functions.
     * @param normalized true for the normalized RBF network.
     */
    public RBFNetwork(T[] x, int[] y, Metric<T> distance, RadialBasisFunction rbf, T[] centers, boolean normalized) {
        this(x, y, distance, rep(rbf, centers.length), centers, normalized);
    }
    
    /**
     * Returns an array of radial basis functions initialized with given values.
     * @param rbf the initial value of array.
     * @param k the size of array.
     * @return an array of radial basis functions initialized with given values
     */
    private static RadialBasisFunction[] rep(RadialBasisFunction rbf, int k) {
        RadialBasisFunction[] arr = new RadialBasisFunction[k];
        Arrays.fill(arr, rbf);
        return arr;
    }
    
    /**
     * Constructor. Learn a RBF network.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param distance the distance metric functor.
     * @param rbf the radial basis functions.
     * @param centers the centers of RBF functions.
     * @param normalized true for the normalized RBF network.
     */
    public RBFNetwork(T[] x, int[] y, Metric<T> distance, RadialBasisFunction[] rbf, T[] centers, boolean normalized) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (rbf.length != centers.length) {
            throw new IllegalArgumentException(String.format("The sizes of RBF functions and centers don't match: %d != %d", rbf.length, centers.length));
        }

        // class label set.
        int[] labels = Math.unique(y);
        Arrays.sort(labels);
        
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]); 
            }
            
            if (i > 0 && labels[i] - labels[i-1] > 1) {
                throw new IllegalArgumentException("Missing class: " + labels[i]+1);                 
            }
        }

        k = labels.length;
        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");            
        }

        this.centers = centers;
        this.distance = distance;
        this.rbf = rbf;
        this.normalized = normalized;
        
        int n = x.length;
        int m = rbf.length;

        DenseMatrix G = Matrix.zeros(n, m+1);
        DenseMatrix b = Matrix.zeros(n, k);
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < m; j++) {
                double r = rbf[j].f(distance.d(x[i], centers[j]));
                G.set(i, j, r);
                sum += r;
            }

            G.set(i, m, 1);

            if (normalized) {
                b.set(i, y[i], sum);
            } else {
                b.set(i, y[i], 1);
            }
        }

        QR qr = G.qr();
        qr.solve(b);

        // Copy the result from b to w
        w = Matrix.zeros(m+1, k);
        for (int j = 0; j < w.ncols(); j++) {
            for (int i = 0; i < w.nrows(); i++) {
                w.set(i, j, b.get(i, j));
            }
        }
    }

    @Override
    public int predict(T x) {
        double[] sumw = new double[k];

        double sum = 0.0;
        for (int i = 0; i < rbf.length; i++) {
            double f = rbf[i].f(distance.d(x, centers[i]));
            sum += f;
            for (int j = 0; j < k; j++) {
                sumw[j] += w.get(i, j) * f;
            }
        }

        if (normalized) {
            for (int j = 0; j < k; j++) {
                sumw[j] = (sumw[j] + w.get(centers.length, j)) / sum;
            }
        } else {
            for (int j = 0; j < k; j++) {
                sumw[j] += w.get(centers.length, j);
            }
        }

        double max = Double.NEGATIVE_INFINITY;
        int y = 0;
        for (int j = 0; j < k; j++) {
            if (max < sumw[j]) {
                max = sumw[j];
                y = j;
            }
        }

        return y;
    }
}
