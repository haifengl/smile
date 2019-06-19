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

package smile.regression;

import java.util.Arrays;
import smile.base.RBF;
import smile.math.distance.Metric;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.QR;
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
 * Popular choices for &phi; comprise the Gaussian function and the so
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
 *
 * <h2>References</h2>
 * <ol>
 * <li> Simon Haykin. Neural Networks: A Comprehensive Foundation (2nd edition). 1999. </li> 
 * <li> T. Poggio and F. Girosi. Networks for approximation and learning. Proc. IEEE 78(9):1484-1487, 1990. </li>
 * <li> Nabil Benoudjit and Michel Verleysen. On the kernel widths in radial-basis function networks. Neural Process, 2003.</li>
 * </ol>
 * 
 * @see RadialBasisFunction
 * @see SVR
 * 
 * @author Haifeng Li
 */
public class RBFNetwork<T> implements Regression<T>, smile.base.RBFNetwork {
    private static final long serialVersionUID = 1L;

    /**
     * The linear weights.
     */
    private double[] w;
    /**
     * The radial basis functions.
     */
    private RBF[] rbf;
    /**
     * True to fit a normalized RBF network.
     */
    private boolean normalized;

    /**
     * Private constructor.
     */
    private RBFNetwork() {

    }

    /**
     * Fits a RBF network.
     * @param x the training dataset.
     * @param y the response variable.
     * @param rbf the radial basis functions.
     */
    public static <T> RBFNetwork<T> fit(T[] x, double[] y, RBF[] rbf) {
        return fit(x, y, rbf);
    }

    /**
     * Fits a RBF network.
     * @param x the training dataset.
     * @param y the response variable.
     * @param rbf the radial basis functions.
     * @param normalized true for the normalized RBF network.
     */
    public static <T> RBFNetwork<T> fit(T[] x, double[] y, RBF[] rbf, boolean normalized) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        RBFNetwork model = new RBFNetwork();
        model.rbf = rbf;
        model.normalized = normalized;
        
        int n = x.length;
        int m = rbf.length;

        DenseMatrix G = Matrix.zeros(n, m);
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

        model.w = new double[m];
        QR qr = G.qr();
        qr.solve(b, model.w);

        return model;
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
