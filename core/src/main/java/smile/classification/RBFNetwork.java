/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.classification;

import smile.base.rbf.RBF;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.rbf.RadialBasisFunction;
import smile.util.IntSet;

import java.io.Serial;
import java.util.Properties;

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
 * The popular choices for &phi; comprise the Gaussian function and the
 * so-called thin plate splines. The advantage of the thin plate splines is that
 * their conditioning is invariant under scalings. Gaussian, multi-quadric
 * and inverse multi-quadric are infinitely smooth and involve a scale
 * or shape parameter, r<sub><small>0</small></sub> {@code > 0}. Decreasing
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
 * @see MLP
 *
 * @param <T> the data type of model input objects.
 *
 * @author Haifeng Li
 */
public class RBFNetwork<T> extends AbstractClassifier<T> {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The number of classes.
     */
    private final int k;
    /**
     * The linear weights.
     */
    private final Matrix w;
    /**
     * The radial basis function.
     */
    private final RBF<T>[] rbf;
    /**
     * True to fit a normalized RBF network.
     */
    private final boolean normalized;

    /**
     * Constructor.
     * @param k the number of classes.
     * @param rbf the radial basis functions.
     * @param w the weights of RBFs.
     * @param normalized True if this is a normalized RBF network.
     */
    public RBFNetwork(int k, RBF<T>[] rbf, Matrix w, boolean normalized) {
        this(k, rbf, w, normalized, IntSet.of(k));
    }

    /**
     * Constructor.
     * @param k the number of classes.
     * @param rbf the radial basis functions.
     * @param w the weights of RBFs.
     * @param normalized True if this is a normalized RBF network.
     * @param labels the class label encoder.
     */
    public RBFNetwork(int k, RBF<T>[] rbf, Matrix w, boolean normalized, IntSet labels) {
        super(labels);
        this.k = k;
        this.rbf = rbf;
        this.w = w;
        this.normalized = normalized;
    }

    /**
     * Fits an RBF network.
     *
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param rbf the radial basis functions.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> RBFNetwork<T> fit(T[] x, int[] y, RBF<T>[] rbf) {
        return fit(x, y, rbf, false);
    }

    /**
     * Fits an RBF network.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param rbf the radial basis functions.
     * @param normalized true for the normalized RBF network.
     * @param <T> the data type.
     * @return the model.
     */
    public static <T> RBFNetwork<T> fit(T[] x, int[] y, RBF<T>[] rbf, boolean normalized) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        int n = x.length;
        int m = rbf.length;

        Matrix G = new Matrix(n, m+1);
        Matrix b = new Matrix(n, k);
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < m; j++) {
                double r = rbf[j].f(x[i]);
                G.set(i, j, r);
                sum += r;
            }

            G.set(i, m, 1);

            if (normalized) {
                b.set(i, codec.y[i], sum);
            } else {
                b.set(i, codec.y[i], 1);
            }
        }

        Matrix.QR qr = G.qr(true);
        qr.solve(b);

        return new RBFNetwork<>(k, rbf, b.submatrix(0, 0, m, k-1), normalized, codec.classes);
    }

    /**
     * Fits an RBF network.
     * @param x training samples.
     * @param y training labels.
     * @param params the hyperparameters.
     * @return the model.
     */
    public static RBFNetwork<double[]> fit(double[][] x, int[] y, Properties params) {
        int neurons = Integer.parseInt(params.getProperty("smile.rbf.neurons", "30"));
        boolean normalize = Boolean.parseBoolean(params.getProperty("smile.rbf.normalize", "false"));
        return RBFNetwork.fit(x, y, RBF.fit(x, neurons), normalize);
    }

    /**
     * Returns true if the model is  normalized.
     * @return true if the model is  normalized.
     */
    public boolean isNormalized() {
        return normalized;
    }

    @Override
    public int predict(T x) {
        int m = rbf.length;
        double[] f = new double[m+1];
        f[m] = 1.0;
        for (int i = 0; i < m; i++) {
            f[i] = rbf[i].f(x);
        }

        double[] sumw = new double[k];
        w.tv(f, sumw);

        return classes.valueOf(MathEx.whichMax(sumw));
    }
}
