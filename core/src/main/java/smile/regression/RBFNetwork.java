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

package smile.regression;

import java.io.Serial;
import java.util.Properties;
import smile.base.rbf.RBF;
import smile.math.matrix.Matrix;
import smile.math.rbf.RadialBasisFunction;

/**
 * Radial basis function network. A radial basis function network is an
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
 * The points c<sub>i</sub> are often called the centers of the RBF networks,
 * which can be randomly selected from training data, or learned by some clustering
 * method (e.g. k-means), or learned together with weight parameters undergo
 * a supervised learning processing (e.g. error-correction learning).
 * <p>
 * Popular choices for &phi; comprise the Gaussian function and the
 * so-called thin plate splines. The advantage of the thin plate splines is that
 * their conditioning is invariant under scaling. Gaussian, multi-quadric
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
 *
 * @param <T> the data type of samples.
 *
 * @author Haifeng Li
 */
public class RBFNetwork<T> implements Regression<T> {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The linear weights.
     */
    private final double[] w;
    /**
     * The radial basis functions.
     */
    private final RBF<T>[] rbf;
    /**
     * True to fit a normalized RBF network.
     */
    private final boolean normalized;

    /**
     * Constructor.
     * @param rbf the radial basis functions.
     * @param w the weights of RBFs.
     * @param normalized True if this is a normalized RBF network.
     */
    public RBFNetwork(RBF<T>[] rbf, double[] w, boolean normalized) {
        this.rbf = rbf;
        this.w = w;
        this.normalized = normalized;
    }

    /**
     * Fits an RBF network.
     * @param x the training dataset.
     * @param y the response variable.
     * @param rbf the radial basis functions.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> RBFNetwork<T> fit(T[] x, double[] y, RBF<T>[] rbf) {
        return fit(x, y, rbf, false);
    }

    /**
     * Fits an RBF network.
     * @param x the training dataset.
     * @param y the response variable.
     * @param rbf the radial basis functions.
     * @param normalized true for the normalized RBF network.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> RBFNetwork<T> fit(T[] x, double[] y, RBF<T>[] rbf, boolean normalized) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        int n = x.length;
        int m = rbf.length;

        Matrix G = new Matrix(n, m);
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < m; j++) {
                double r = rbf[j].f(x[i]);
                G.set(i, j, r);
                sum += r;
            }

            if (normalized) {
                b[i] = sum * y[i];
            } else {
                b[i] = y[i];
            }
        }

        Matrix.QR qr = G.qr(true);
        double[] w = qr.solve(b);

        return new RBFNetwork<>(rbf, w, normalized);
    }

    /**
     * Fits an RBF network.
     * @param x training samples.
     * @param y the response variable.
     * @param params the hyperparameters.
     * @return the model.
     */
    public static RBFNetwork<double[]> fit(double[][] x, double[] y, Properties params) {
        int neurons = Integer.parseInt(params.getProperty("smile.rbf.neurons", "30"));
        boolean normalize = Boolean.parseBoolean(params.getProperty("smile.rbf.normalize", "false"));
        return fit(x, y, RBF.fit(x, neurons), normalize);
    }

    /**
     * Returns true if the model is normalized.
     * @return true if the model is normalized.
     */
    public boolean isNormalized() {
        return normalized;
    }

    @Override
    public double predict(T x) {
        double sum = 0.0, sumw = 0.0;
        for (int i = 0; i < rbf.length; i++) {
            double f = rbf[i].f(x);
            sumw += w[i] * f;
            sum += f;
        }

        return normalized ? sumw / sum : sumw;
    }
}
