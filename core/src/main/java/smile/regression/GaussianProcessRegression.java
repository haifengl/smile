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

package smile.regression;

import smile.math.kernel.MercerKernel;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Cholesky;
import smile.math.matrix.LU;
import smile.math.matrix.EVD;

/**
 * Gaussian Process for Regression. A Gaussian process is a stochastic process
 * whose realizations consist of random values associated with every point in
 * a range of times (or of space) such that each such random variable has
 * a normal distribution. Moreover, every finite collection of those random
 * variables has a multivariate normal distribution.
 * <p>
 * A Gaussian process can be used as a prior probability distribution over
 * functions in Bayesian inference. Given any set of N points in the desired
 * domain of your functions, take a multivariate Gaussian whose covariance
 * matrix parameter is the Gram matrix of N points with some desired kernel,
 * and sample from that Gaussian. Inference of continuous values with a
 * Gaussian process prior is known as Gaussian process regression.
 * <p>
 * The fitting is performed in the reproducing kernel Hilbert space with
 * the "kernel trick". The loss function is squared-error. This also arises
 * as the kriging estimate of a Gaussian random field in spatial statistics.
 * <p>
 * A significant problem with Gaussian process prediction is that it typically
 * scales as O(n<sup>3</sup>). For large problems (e.g. n &gt; 10,000) both
 * storing the Gram matrix and solving the associated linear systems are
 * prohibitive on modern workstations. An extensive range of proposals have
 * been suggested to deal with this problem. A popular approach is the
 * reduced-rank Approximations of the Gram Matrix, known as Nystrom approximation.
 * Greedy approximation is another popular approach that uses an active set of
 * training points of size m selected from the training set of size n &gt; m.
 * We assume that it is impossible to search for the optimal subset of size m
 * due to combinatorics. The points in the active set could be selected
 * randomly, but in general we might expect better performance if the points
 * are selected greedily w.r.t. some criterion. Recently, researchers had
 * proposed relaxing the constraint that the inducing variables must be a
 * subset of training/test cases, turning the discrete selection problem
 * into one of continuous optimization.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Carl Edward Rasmussen and Chris Williams. Gaussian Processes for Machine Learning, 2006.</li>
 * <li> Joaquin Quinonero-candela,  Carl Edward Ramussen,  Christopher K. I. Williams. Approximation Methods for Gaussian Process Regression. 2007. </li>
 * <li> T. Poggio and F. Girosi. Networks for approximation and learning. Proc. IEEE 78(9):1484-1487, 1990. </li>
 * <li> Kai Zhang and James T. Kwok. Clustered Nystrom Method for Large Scale Manifold Learning and Dimension Reduction. IEEE Transactions on Neural Networks, 2010. </li>
 * <li> </li>
 * </ol>
 * @author Haifeng Li
 */
public class GaussianProcessRegression {
    /**
     * Fits a regular Gaussian process model.
     * @param x the training dataset.
     * @param y the response variable.
     * @param kernel the Mercer kernel.
     * @param lambda the shrinkage/regularization parameter.
     */
    public static <T> KernelMachine<T> fit(T[] x, double[] y, MercerKernel<T> kernel, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid regularization parameter lambda = " + lambda);
        }

        int n = x.length;

        DenseMatrix K = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double k = kernel.k(x[i], x[j]);
                K.set(i, j, k);
                K.set(j, i, k);
            }

            K.add(i, i, lambda);
        }

        Cholesky cholesky = K.cholesky();
        double[] w = y.clone();
        cholesky.solve(w);

        return new KernelMachine<>(kernel, x, w);
    }

    /**
     * Fits an approximate Gaussian process model by the method of subset of regressors.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input, which are pre-selected or inducing samples
     * acting as active set of regressors. In simple case, these can be chosen
     * randomly from the training set or as the centers of k-means clustering.
     * @param kernel the Mercer kernel.
     * @param lambda the shrinkage/regularization parameter.
     */
    public static <T> KernelMachine<T> fit(T[] x, double[] y, T[] t, MercerKernel<T> kernel, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid regularization parameter lambda = " + lambda);
        }

        int n = x.length;
        int m = t.length;

        DenseMatrix G = Matrix.zeros(n, m);
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                G.set(i, j, kernel.k(x[i], t[j]));
            }
        }

        DenseMatrix K = G.ata();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j <= i; j++) {
                K.add(i, j, lambda * kernel.k(t[i], t[j]));
                K.set(j, i, K.get(i, j));
            }
        }

        double[] w = new double[m];
        G.atx(y, w);

        LU lu = K.lu(true);
        lu.solve(w);

        return new KernelMachine<>(kernel, t, w);
    }

    /**
     * Fits an approximate Gaussian process model with Nystrom approximation of kernel matrix.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input for Nystrom approximation. Commonly, these
     * can be chosen as the centers of k-means clustering.
     * @param kernel the Mercer kernel.
     * @param lambda the shrinkage/regularization parameter.
     */
    public static <T> KernelMachine<T> nystrom(T[] x, double[] y, T[] t, MercerKernel<T> kernel, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid regularization parameter lambda = " + lambda);
        }

        int n = x.length;
        int m = t.length;

        DenseMatrix E = Matrix.zeros(n, m);
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                E.set(i, j, kernel.k(x[i], t[j]));
            }
        }

        DenseMatrix W = Matrix.zeros(m, m);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j <= i; j++) {
                double k = kernel.k(t[i], t[j]);
                W.set(i, j, k);
                W.set(j, i, k);
            }
        }

        W.setSymmetric(true);
        EVD eigen = W.eigen();
        DenseMatrix U = eigen.getEigenVectors();
        DenseMatrix D = eigen.getD();
        for (int i = 0; i < m; i++) {
            D.set(i, i, 1.0 / Math.sqrt(D.get(i, i)));
        }

        DenseMatrix UD = U.abmm(D);
        DenseMatrix UDUt = UD.abtmm(U);
        DenseMatrix L = E.abmm(UDUt);
        
        DenseMatrix LtL = L.ata();
        for (int i = 0; i < m; i++) {
            LtL.add(i, i, lambda);
        }

        Cholesky chol = LtL.cholesky();
        DenseMatrix invLtL = chol.inverse();
        DenseMatrix K = L.abmm(invLtL).abtmm(L);

        double[] w = new double[n];
        K.atx(y, w);
        
        for (int i = 0; i < n; i++) {
            w[i] = (y[i] - w[i]) / lambda;
        }

        return new KernelMachine<>(kernel, x, w);
    }
}
