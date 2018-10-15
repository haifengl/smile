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
import smile.math.matrix.Matrix;
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
    private int p;
    /**
     * The dimension of input space.
     */
    private int n;
    /**
     * constant to check if convergence on one component.
     */
    private double convergence;
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
    private NegEntropyFunc funcMode = NegEntropyFunc.LOGCOSH;

    /**
     * functions to calculate derivative and second-order derivative
     */
    public enum NegEntropyFunc {
        /**
         * This Negative Entropy function is described as a "good general-purpose
         * contrast function" in the original paper, and the default method used.
         */
        LOGCOSH,
        /**
         * This is function according to the paper may be better than {@link #LOGCOSH}
         * when the independent components are highly super-Gaussian, or when robustness
         * is very important
         */
        EXP
    }

    /**
     * Constructor. Learn independent component analysis from the data.
     * 
     * @param p
     *            target independent component count
     */
    public ICA(double[][] data, int p) {
        this(data, p, 1E-5, 10000);
    }

    /**
     * Constructor. Learn independent component analysis from the data.
     * 
     * @param p
     *            target independent component count
     * @param cong
     *            convergence threshold on component search
     * @param maxIter
     *            maximum iteration on component search
     */
    public ICA(double[][] data, int p, double cong, int maxIter) {
        int m = data.length;
        this.n = data[0].length;
        this.p = p;
        this.convergence = cong;
        this.maxIter = maxIter;

        projection = Matrix.zeros(p, n);
        DenseMatrix m1 = DenseMatrix.ones(m, 1);

        DenseMatrix[] wps = new DenseMatrix[p];
        for (int i = 0; i < p; i++) {
            wps[i] = DenseMatrix.randn(1, n);
            wps[i] = wps[i].div(wps[i].normFro());
        }

        DenseMatrix whitened = whiteData(data);
        for (int i = 0; i < p; i++) {
            DenseMatrix lastwp = wps[i].copy();

            double diff = Double.MAX_VALUE;
            int iterCnt = 0;
            while (diff > this.convergence) {
                // step 1 calculate derivative from non-linear functions
                DenseMatrix proj = lastwp.abmm(whitened.transpose());
                DenseMatrix projFD = proj.copy();
                for (int idx = 0; idx < projFD.nrows(); idx++) {
                    for (int idxc = 0; idxc < projFD.ncols(); idxc++) {
                        projFD.set(idx, idxc,
                                funcMode == NegEntropyFunc.LOGCOSH ? getDeriv1WithLOGCOSH(projFD.get(idx, idxc))
                                        : getDeriv1WithEXP(projFD.get(idx, idxc)));
                    }
                }
                DenseMatrix projSD = proj.copy();
                for (int idx = 0; idx < projSD.nrows(); idx++) {
                    for (int idxc = 0; idxc < projSD.ncols(); idxc++) {
                        projSD.set(idx, idxc,
                                funcMode == NegEntropyFunc.LOGCOSH ? getDeriv2WithLOGCOSH(projSD.get(idx, idxc))
                                        : getDeriv2WithEXP(projSD.get(idx, idxc)));
                    }
                }
                DenseMatrix wp = (whitened.transpose().abmm(projFD.transpose())).transpose()
                        .sub(projSD.abmm(m1).abmm(lastwp)).div(m);
                // step 2 make sure independent components orthogonal
                DenseMatrix sump = DenseMatrix.zeros(1, n);
                for (int pi = 0; pi < i; pi++) {
                    DenseMatrix mm = wps[pi].transpose().abmm(wps[pi]);
                    sump.add(wp.abmm(mm));
                }
                wp = wp.sub(sump);
                wp = wp.div(wp.normFro());

                // check if convergence
                DenseMatrix dot = wp.abmm(lastwp.transpose());
                diff = Math.abs(Math.abs(dot.array()[0][0]) - 1);

                lastwp = wp;

                iterCnt++;
                if (iterCnt > this.maxIter) {
                    throw new IllegalArgumentException(
                            String.format("fail to find the No.%d Independent Component within given iterations: %d",
                                    (i + 1), maxIter));
                }
            }
            wps[i] = lastwp;
            for (int j = 0; j < n; j++) {
                projection.set(i, j, lastwp.get(0, j));
            }
        }

        pmu = Math.colMeans(projection.transpose().array());
    }

    @Override
    public double[] project(double[] x) {
        if (x.length != n) {
            throw new IllegalArgumentException(
                    String.format("Invalid input vector size: %d, expected: %d", x.length, n));
        }

        double[] y = new double[p];
        projection.ax(x, y);
        Math.minus(y, pmu);
        return y;
    }

    @Override
    public double[][] project(double[][] x) {
        if (x[0].length != n) {
            throw new IllegalArgumentException(
                    String.format("Invalid input vector size: %d, expected: %d", x[0].length, n));
        }

        double[][] y = new double[x.length][p];
        for (int i = 0; i < x.length; i++) {
            projection.ax(x[i], y[i]);
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
     * @return the functor {@link NegEntropyFunc} to get first or second order
     *         derivative
     */
    public NegEntropyFunc getFuncMode() {
        return funcMode;
    }

    /**
     * set the functor {@link NegEntropyFunc} to get first or second order
     * derivative
     * 
     * @param funcMode
     */
    public void setFuncMode(NegEntropyFunc funcMode) {
        this.funcMode = funcMode;
    }

    /**
     * Before applying ICA, we need to do whitening against the raw dataset.
     * 
     * @param data
     * @return whitened dataset
     */
    private DenseMatrix whiteData(double[][] data) {
        int m = data.length;
        // center data
        double[] mu = Math.colMeans(data);
        DenseMatrix centered = Matrix.newInstance(data);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < m; i++) {
                centered.sub(i, j, mu[j]);
            }
        }

        // covariance matrix on centered data.
        DenseMatrix T = Matrix.zeros(n, n);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int l = 0; l <= j; l++) {
                    T.add(j, l, centered.get(i, j) * centered.get(i, l));
                }
            }
        }
        for (int j = 0; j < n; j++) {
            for (int l = 0; l <= j; l++) {
                T.div(j, l, m);
                T.set(l, j, T.get(j, l));
            }
        }

        T.setSymmetric(true);
        EVD eigen = T.eigen();
        DenseMatrix D = eigen.getEigenVectors();
        double[] v = eigen.getEigenValues();
        for (int i = 0; i < v.length; i++) {
            v[i] = 1 / Math.sqrt(v[i]);
        }
        DenseMatrix V = DenseMatrix.diag(v);
        DenseMatrix white = D.abmm(V).abmm(D.transpose());

        DenseMatrix ct = centered.transpose();
        ct = white.abmm(ct);

        return ct.transpose();
    }

    /**
     * calculate first order derivative according to {@link NegEntropyFunc#LOGCOSH}
     * functor
     * 
     * @param val
     * @return first order derivative according to {@link NegEntropyFunc#LOGCOSH}
     *         functor
     */
    private double getDeriv1WithLOGCOSH(double val) {
        return Math.tanh(val);
    }

    /**
     * calculate second order derivative according to {@link NegEntropyFunc#LOGCOSH}
     * functor
     * 
     * @param val
     * @return second order derivative according to {@link NegEntropyFunc#LOGCOSH}
     *         functor
     */
    private double getDeriv2WithLOGCOSH(double val) {
        return 1 - Math.sqr(Math.tanh(val));
    }

    /**
     * calculate first order derivative according to {@link NegEntropyFunc#EXP}
     * functor
     * 
     * @param val
     * @return first order derivative according to {@link NegEntropyFunc#EXP}
     *         functor
     */
    private double getDeriv1WithEXP(double val) {
        return val * Math.exp(-0.5 * Math.sqr(val));
    }

    /**
     * calculate second order derivative according to {@link NegEntropyFunc#EXP}
     * functor
     * 
     * @param val
     * @return second order derivative according to {@link NegEntropyFunc#EXP}
     *         functor
     */
    private double getDeriv2WithEXP(double val) {
        return (1 - Math.sqr(val)) * Math.exp(-0.5 * Math.sqr(val));
    }

}