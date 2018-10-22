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
package smile.projection;

import java.io.Serializable;
import smile.math.Math;
import smile.math.NonquadraticNonlinearFunc;
import smile.math.matrix.Matrix;
import smile.stat.distribution.GaussianDistribution;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;

/**
 * Independent Component Analysis (ICA) is a computational method for separating
 * a multivariate signal into additive subcomponents.
 * <p>
 * We use FastICA for this implementation, which is an efficient and popular
 * algorithm for independent component analysis invented by Aapo Hyvärinen at
 * Helsinki University of Technology.
 * <p>
 * Like most ICA algorithms, FastICA seeks an orthogonal rotation of prewhitened
 * data, through a fixed-point iteration scheme, that maximizes a measure of
 * non-Gaussianity of the rotated components.
 * <p>
 * Non-gaussianity serves as a proxy for statistical independence, which is a
 * very strong condition and requires infinite data to verify. FastICA can also
 * be alternatively derived as an approximative Newton iteration.
 * <p>
 * Typical use case is 'cocktail party problem' where to identify original
 * speakers from mixed audio recordings.
 * 
 * <h2>References</h2>
 * <ol>
 * <li>Aapo Hyvärinen: Fast and robust fixed-point algorithms for independent
 * component analysis, 1999</li>
 * <li>Aapo Hyvärinen, Erkki Oja: Independent component analysis: Algorithms and
 * applications, 2000</li>
 * </ol>
 * 
 * @see PCA
 * 
 * @author rayeaster
 */
public class ICA implements Projection<double[]>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The dimension of feature space.
     */
    private int C;
    /**
     * The dimension of input space.
     */
    private int m;
    /**
     * constant to check if convergence on one component.
     */
    private double tolerance;
    /**
     * constant for maximum iteration for one component search.
     */
    private int maxIter;
    /**
     * The projected sample mean.
     */
    private double[] pmu;
    /**
     * Projection matrix.
     */
    private DenseMatrix projection;

    /**
     * determine the functions to calculate derivative and second-order derivative
     */
    private NonquadraticNonlinearFunc nnf;

    /**
     * This function is described as a "good general-purpose function" in the
     * original paper, and the default method used.
     */
    public static class LogCoshNNF implements NonquadraticNonlinearFunc {

        @Override
        public double f(double x) {
            return Math.log(Math.cosh(x));
        }

        @Override
        public double g(double x) {
            return Math.tanh(x);
        }

        @Override
        public double g2(double x) {
            return 1 - Math.sqr(Math.tanh(x));
        }

    }

    /**
     * This is function according to the paper may be better than {@link LogCoshNNF}
     * when the independent components are highly super-Gaussian, or when robustness
     * is very important
     */
    public static class ExpNNF implements NonquadraticNonlinearFunc {

        @Override
        public double f(double x) {
            return (-1) * Math.exp(-0.5 * Math.sqr(x));
        }

        @Override
        public double g(double x) {
            return x * Math.exp(-0.5 * Math.sqr(x));
        }

        @Override
        public double g2(double x) {
            return (1 - Math.sqr(x)) * Math.exp(-0.5 * Math.sqr(x));
        }

    }

    /**
     * Constructor. Learn independent component analysis from the data. *
     * 
     * @param data
     *            train data set
     * @param p
     *            target independent component count
     */
    public ICA(double[][] data, int p) {
        this(data, p, 1E-5, 10000, new LogCoshNNF());
    }

    /**
     * Constructor. Learn independent component analysis from the data. *
     * 
     * @param data
     *            train data set
     * @param p
     *            target independent component count
     * @param nnf
     *            see {@link NonquadraticNonlinearFunc}
     */
    public ICA(double[][] data, int p, NonquadraticNonlinearFunc nnf) {
        this(data, p, 1E-5, 10000, nnf);
    }

    /**
     * Constructor. Learn independent component analysis from the data.
     * 
     * @param data
     *            train data set
     * @param p
     *            target independent component count
     * @param tol
     *            convergence threshold on component search
     * @param maxIter
     *            maximum iteration on component search
     * @param nnf
     *            see {@link NonquadraticNonlinearFunc}
     */
    public ICA(double[][] data, int p, double tol, int maxIter, NonquadraticNonlinearFunc nnf) {
        int n = data.length;// number of samples
        this.m = data[0].length;// number of features/signals
        this.C = p; // number of desired components
        if (C < 1 || C > m) {
            throw new IllegalArgumentException("Invalid dimension of feature space: " + C);
        }

        this.tolerance = tol;
        if (tolerance < 0) {
            throw new IllegalArgumentException("Invalid tolerance for convergence check: " + tolerance);
        }
        this.maxIter = maxIter;

        projection = Matrix.zeros(p, m);

        GaussianDistribution g = new GaussianDistribution(0, 1);
        double[][] wps = new double[p][m];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < m; j++) {
                wps[i][j] = g.rand();
            }
            // normalize
            double norm = Math.norm2(wps[i]);
            for (int j = 0; j < m; j++) {
                wps[i][j] /= norm;
            }
        }

        DenseMatrix X = whiteData(data);
        for (int i = 0; i < p; i++) {
            double[] lastwp = wps[i];

            double diff = Double.MAX_VALUE;
            int iterCnt = 0;
            while (diff > this.tolerance) {
                // step 1 calculate derivative of projection from non-linear functions
                double[] proj = new double[n];
                proj = X.ax(lastwp, proj);

                double[] projFD = new double[n];
                for (int idx = 0; idx < n; idx++) {
                    projFD[idx] = nnf.g(proj[idx]);
                }

                double[] projSD = new double[n];
                double sumProjSD = 0;
                for (int idx = 0; idx < n; idx++) {
                    projSD[idx] = nnf.g2((proj[idx]));
                    sumProjSD += projSD[idx];
                }

                double[] wp = new double[m];
                wp = X.atx(projFD, wp);
                double[] sub = new double[m];
                for (int idx = 0; idx < m; idx++) {
                    sub[idx] = sumProjSD * lastwp[idx];
                }
                for (int idx = 0; idx < m; idx++) {
                    wp[idx] -= sub[idx];
                    wp[idx] /= n;
                }

                // step 2 make sure independent components orthogonal
                double[] accumulated = new double[m];
                for (int pi = 0; pi < i; pi++) {
                    double dot = Math.dot(wp, wps[pi]);
                    for (int idx = 0; idx < m; idx++) {
                        accumulated[idx] += dot * wps[pi][idx];
                    }
                }
                for (int idx = 0; idx < m; idx++) {
                    wp[idx] -= accumulated[idx];
                }

                // normalize
                double norm = Math.norm2(wp);
                for (int j = 0; j < m; j++) {
                    wp[j] /= norm;
                }

                // step 3 check if convergence
                diff = Math.abs(Math.abs(Math.dot(wp, lastwp)) - 1);

                lastwp = wp;

                iterCnt++;
                if (iterCnt > this.maxIter) {
                    throw new IllegalArgumentException(
                            String.format("fail to find the No.%d Independent Component within given iterations: %d",
                                    (i + 1), maxIter));
                }
            }
            wps[i] = lastwp;
            for (int j = 0; j < m; j++) {
                projection.set(i, j, lastwp[j]);
            }
        }

        pmu = Math.colMeans(projection.transpose().array());
    }

    @Override
    public double[] project(double[] x) {
        double[] y = new double[C];
        projection.ax(x, y);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        if (x[0].length != m) {
            throw new IllegalArgumentException(
                    String.format("Invalid input vector size: %d, expected: %d", x[0].length, m));
        }

        double[][] y = new double[x.length][C];
        DenseMatrix X = whiteData(x);
        double[][] data = X.array();
        for (int i = 0; i < data.length; i++) {
            projection.ax(data[i], y[i]);
            Math.minus(y[i], pmu);
        }
        return y;
    }

    /**
     * 
     * @return the projection matrix
     */
    public DenseMatrix getProjection() {
        return projection;
    }

    /**
     * 
     * @return the functor {@link NonquadraticNonlinearFunc} to get first or second
     *         order derivative
     */
    public NonquadraticNonlinearFunc getNonquadraticNonlinearFunc() {
        return nnf;
    }

    /**
     * Before applying ICA, we need to do whitening against the raw dataset.
     * 
     * @param data
     * @return whitened dataset
     */
    private DenseMatrix whiteData(double[][] data) {
        // covariance matrix on centered data.
        DenseMatrix centered = Matrix.newInstance(data);
        DenseMatrix T = (DenseMatrix) Math.cov(centered, centered.colMeans());

        T.setSymmetric(true);
        EVD eigen = T.eigen();
        DenseMatrix E = eigen.getEigenVectors();
        double[] d = eigen.getEigenValues();
        double tol = tolerance * tolerance;
        for (int i = 0; i < d.length; i++) {
            if (d[i] < tol) {
                throw new IllegalArgumentException("The covariance matrix is close to singular.");
            }
            d[i] = 1 / Math.sqrt(d[i]);
        }
        DenseMatrix D = DenseMatrix.diag(d);
        DenseMatrix white = E.abmm(D).abtmm(E);

        DenseMatrix ct = centered.abtmm(white);
        return ct;
    }

}