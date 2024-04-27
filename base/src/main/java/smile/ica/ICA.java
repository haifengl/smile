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

package smile.ica;

import java.io.Serializable;
import java.util.Properties;
import smile.math.DifferentiableFunction;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.stat.distribution.GaussianDistribution;

/**
 * Independent Component Analysis (ICA) is a computational method for separating
 * a multivariate signal into additive components. FastICA is an efficient
 * algorithm for ICA invented by Aapo Hyvärinen.
 * <p>
 * Like most ICA algorithms, FastICA seeks an orthogonal rotation of prewhitened
 * data, through a fixed-point iteration scheme, that maximizes a measure of
 * non-Gaussianity of the rotated components. Non-gaussianity serves as a proxy
 * for statistical independence, which is a very strong condition and requires
 * infinite data to verify. To measure non-Gaussianity, FastICA relies on a
 * non-quadratic nonlinear function f(u), its first derivative g(u),
 * and its second derivative g2(u).
 * <p>
 * A simple application of ICA is the cocktail party problem, where the
 * underlying speech signals are separated from a sample data consisting
 * of people talking simultaneously in a room. Usually the problem is
 * simplified by assuming no time delays or echoes.
 * <p>
 * An important note to consider is that if N sources are present,
 * at least N observations (e.g. microphones if the observed signal
 * is audio) are needed to recover the original signals.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Aapo Hyvärinen: Fast and robust fixed-point algorithms for independent component analysis, 1999</li>
 * <li>Aapo Hyvärinen, Erkki Oja: Independent component analysis: Algorithms and applications, 2000</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class ICA implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ICA.class);

    /**
     * The independent components (row-wise).
     */
    public final double[][] components;

    /**
     * Constructor.
     * @param components each row is an independent component.
     */
    private ICA(double[][] components) {
        this.components = components;
    }

    /**
     * Fits independent component analysis.
     *
     * @param data training data. The number of columns corresponding with the
     *             number of samples of mixed signals and the number of rows
     *             corresponding with the number of independent source signals.
     * @param p the number of independent components.
     * @return the model.
     */
    public static ICA fit(double[][] data, int p) {
        return fit(data, p, new Properties());
    }

    /**
     * Fits independent component analysis.
     *
     * @param data training data. The number of columns corresponding with the
     *             number of samples of mixed signals and the number of rows
     *             corresponding with the number of independent source signals.
     * @param p the number of independent components.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static ICA fit(double[][] data, int p, Properties params) {
        DifferentiableFunction f;
        String contrast = params.getProperty("smile.ica.contrast", "LogCosh");
        switch (contrast) {
            case "LogCosh":
                f = new LogCosh();
                break;
            case "Gaussian":
                f = new Exp();
                break;
            default:
                throw new IllegalArgumentException("Unsupported contrast function: " + contrast);
        }
        double tol = Double.parseDouble(params.getProperty("smile.ica.tolerance", "1E-4"));
        int maxIter = Integer.parseInt(params.getProperty("smile.ica.iterations", "100"));
        return fit(data, p, f, tol, maxIter);
    }


    /**
     * Fits independent component analysis.
     *
     * @param data training data.
     * @param p the number of independent components.
     * @param contrast the contrast function which is capable of separating or
     *                 extracting independent sources from a linear mixture.
     *                 It must be a non-quadratic non-linear function that
     *                 has second-order derivative.
     * @param tol the tolerance of convergence test.
     * @param maxIter the maximum number of iterations.
     * @return the model.
     */
    public static ICA fit(double[][] data, int p, DifferentiableFunction contrast, double tol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = data[0].length;
        int m = data.length;
        if (p < 1 || p > m) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + p);
        }

        GaussianDistribution g = new GaussianDistribution(0, 1);
        double[][] W = new double[p][n];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < n; j++) {
                W[i][j] = g.rand();
            }
            MathEx.unitize(W[i]);
        }

        Matrix X = whiten(data);
        double[] wold = new double[n];
        double[] wdif = new double[n];
        double[] gwX = new double[m];
        double[] g2w = new double[n];

        for (int i = 0; i < p; i++) {
            double[] w = W[i];

            double diff = Double.MAX_VALUE;
            for (int iter = 0; iter < maxIter && diff > tol; iter++) {
                System.arraycopy(w, 0, wold, 0, n);

                // Calculate derivative of projection
                double[] wX = new double[m];
                X.tv(w, wX);

                double g2 = 0.0;
                for (int j = 0; j < m; j++) {
                    gwX[j] = contrast.g(wX[j]);
                    g2 += contrast.g2(wX[j]);
                }

                for (int j = 0; j < n; j++) {
                    g2w[j] = w[j] * g2;
                }

                X.mv(gwX, w);

                for (int j = 0; j < n; j++) {
                    w[j] = (w[j] - g2w[j]) / m;
                }

                // Orthogonalization of independent components
                for (int k = 0; k < i; k++) {
                    double[] wk = W[k];
                    double wkw = MathEx.dot(W[k], w);
                    for (int j = 0; j < n; j++) {
                        w[j] -= wkw * wk[j];
                    }
                }

                // normalize
                MathEx.unitize2(w);

                // Test for termination condition. Note that the algorithm has
                // converged if the direction of w and wOld is the same, this
                // is why we test the two cases.check if convergence
                for (int j = 0; j < n; j++) {
                    wdif[j] = (w[j] - wold[j]);
                }
                double n1 = MathEx.norm(wdif);

                for (int j = 0; j < n; j++) {
                    wdif[j] = (w[j] + wold[j]);
                }
                double n2 = MathEx.norm(wdif);

                diff = Math.min(n1, n2);
            }

            if (diff > tol) {
                logger.warn(String.format("Component %d did not converge in %d iterations.", i, maxIter));
            }
        }

        return new ICA(W);
    }

    /**
     * The input data matrix must be prewhitened, or centered and whitened,
     * before applying the FastICA algorithm to it.
     *
     * @param data the raw data.
     * @return the whitened data
     */
    private static Matrix whiten(double[][] data) {
        // covariance matrix on centered data.
        double[] mean = MathEx.rowMeans(data);
        Matrix X = Matrix.of(data).transpose();
        int n = X.nrow();
        int m = X.ncol();
        for (int j = 0; j < m; j++) {
            double mu = mean[j];
            for (int i = 0; i < n; i++) {
                X.sub(i, j, mu);
            }
        }

        Matrix XtX = X.ata();
        Matrix.EVD eigen = XtX.eigen(false, true, true);
        Matrix E = eigen.Vr;
        Matrix Y = X.mm(E);

        double[] d = eigen.wr;
        for (int i = 0; i < d.length; i++) {
            if (d[i] < 1E-8) {
                throw new IllegalArgumentException(String.format("Covariance matrix (column %d) is close to singular.", i));
            }
            d[i] = 1 / Math.sqrt(d[i]);
        }

        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                Y.mul(i, j, d[j]);
            }
        }

        return Y;
    }
}