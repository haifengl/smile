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
package smile.stat.distribution;

import smile.math.matrix.Cholesky;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;

/**
 * Multivariate Gaussian distribution.
 *
 * @see GaussianDistribution
 *
 * @author Haifeng Li
 */
public class MultivariateGaussianDistribution extends AbstractMultivariateDistribution implements MultivariateExponentialFamily {

    private static final double LOG2PIE = Math.log(2 * Math.PI * Math.E);
    double[] mu;
    DenseMatrix sigma;
    boolean diagonal;
    private int dim;
    private DenseMatrix sigmaInv;
    private DenseMatrix sigmaL;
    private double sigmaDet;
    private double pdfConstant;
    private int numParameters;

    /**
     * Constructor. The distribution will have a diagonal covariance matrix of
     * the same variance.
     *
     * @param mean mean vector.
     * @param var variance.
     */
    public MultivariateGaussianDistribution(double[] mean, double var) {
        if (var <= 0) {
            throw new IllegalArgumentException("Variance is not positive: " + var);
        }

        mu = new double[mean.length];
        sigma = Matrix.zeros(mu.length, mu.length);
        for (int i = 0; i < mu.length; i++) {
            mu[i] = mean[i];
            sigma.set(i, i, var);
        }

        diagonal = true;
        numParameters = mu.length + 1;

        init();
    }

    /**
     * Constructor. The distribution will have a diagonal covariance matrix.
     * Each element has different variance.
     *
     * @param mean mean vector.
     * @param var variance vector.
     */
    public MultivariateGaussianDistribution(double[] mean, double[] var) {
        if (mean.length != var.length) {
            throw new IllegalArgumentException("Mean vector and covariance matrix have different dimension");
        }

        mu = new double[mean.length];
        sigma = Matrix.diag(var);
        for (int i = 0; i < mu.length; i++) {
            if (var[i] <= 0) {
                throw new IllegalArgumentException("Variance is not positive: " + var[i]);
            }

            mu[i] = mean[i];
        }

        diagonal = true;
        numParameters = 2 * mu.length;

        init();
    }

    /**
     * Constructor.
     *
     * @param mean mean vector.
     * @param cov covariance matrix.
     */
    public MultivariateGaussianDistribution(double[] mean, double[][] cov) {
        if (mean.length != cov.length) {
            throw new IllegalArgumentException("Mean vector and covariance matrix have different dimension");
        }

        mu = new double[mean.length];
        sigma = Matrix.newInstance(cov);
        for (int i = 0; i < mu.length; i++) {
            mu[i] = mean[i];
        }

        diagonal = false;
        numParameters = mu.length + mu.length * (mu.length + 1) / 2;

        init();
    }

    /**
     * Constructor. Mean and covariance will be estimated from the data by MLE.
     * @param data the training data.
     */
    public MultivariateGaussianDistribution(double[][] data) {
        this(data, false);
    }

    /**
     * Constructor. Mean and covariance will be estimated from the data by MLE.
     * @param data the training data.
     * @param diagonal true if covariance matrix is diagonal.
     */
    public MultivariateGaussianDistribution(double[][] data, boolean diagonal) {
        this.diagonal = diagonal;
        mu = Math.colMeans(data);

        if (diagonal) {
            sigma = Matrix.zeros(data[0].length, data[0].length);
            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < mu.length; j++) {
                    sigma.add(j, j, (data[i][j] - mu[j]) * (data[i][j] - mu[j]));
                }
            }

            for (int j = 0; j < mu.length; j++) {
                sigma.div(j, j, (data.length - 1));
            }
        } else {
            sigma = Matrix.newInstance(Math.cov(data, mu));
        }

        numParameters = mu.length + mu.length * (mu.length + 1) / 2;

        init();
    }

    /**
     * Initialize the object.
     */
    private void init() {
        dim = mu.length;
        Cholesky cholesky = sigma.cholesky(false);
        sigmaInv = cholesky.inverse();
        sigmaDet = cholesky.det();
        sigmaL = cholesky.getL();
        pdfConstant = (dim * Math.log(2 * Math.PI) + Math.log(sigmaDet)) / 2.0;
    }

    /**
     * Returns true if the covariance matrix is diagonal.
     * @return true if the covariance matrix is diagonal
     */
    public boolean isDiagonal() {
        return diagonal;
    }

    @Override
    public int npara() {
        return numParameters;
    }

    @Override
    public double entropy() {
        return (dim * LOG2PIE + Math.log(sigmaDet)) / 2;
    }

    @Override
    public double[] mean() {
        return mu;
    }

    @Override
    public double[][] cov() {
        return sigma.array();
    }

    /**
     * Returns the scatter of distribution, which is defined as |&Sigma;|.
     */
    public double scatter() {
        return sigmaDet;
    }

    @Override
    public double logp(double[] x) {
        if (x.length != dim) {
            throw new IllegalArgumentException("Sample has different dimension.");
        }

        double[] v = x.clone();
        Math.minus(v, mu);
        double result = sigmaInv.xax(v) / -2.0;
        return result - pdfConstant;
    }

    @Override
    public double p(double[] x) {
        return Math.exp(logp(x));
    }

    /**
     * Algorithm from Alan Genz (1992) Numerical Computation of 
     * Multivariate Normal Probabilities, Journal of Computational and 
     * Graphical Statistics, pp. 141-149.
     *
     * The difference between returned value and the true value of the
     * CDF is less than 0.001 in 99.9% time. The maximum number of iterations
     * is set to 10000.
     */
    @Override
    public double cdf(double[] x) {
        if (x.length != dim) {
            throw new IllegalArgumentException("Sample has different dimension.");
        }

        int Nmax = 10000;
        double alph = GaussianDistribution.getInstance().quantile(0.999);
        double errMax = 0.001;

        double[] v = x.clone();
        Math.minus(v, mu);

        double p = 0.0;
        double varSum = 0.0;

        // d is always zero
        double[] e = new double[dim];
        double[] f = new double[dim];
        e[0] = GaussianDistribution.getInstance().cdf(v[0] / sigmaL.get(0, 0));
        f[0] = e[0];

        double[] y = new double[dim];

        double err = 2 * errMax;
        int N;
        for (N = 1; err > errMax && N <= Nmax; N++) {
            double[] w = Math.random(dim - 1);
            for (int i = 1; i < dim; i++) {
                y[i - 1] = GaussianDistribution.getInstance().quantile(w[i - 1] * e[i - 1]);
                double q = 0.0;
                for (int j = 0; j < i; j++) {
                    q += sigmaL.get(i, j) * y[j];
                }

                e[i] = GaussianDistribution.getInstance().cdf((v[i] - q) / sigmaL.get(i, i));
                f[i] = e[i] * f[i - 1];
            }

            double del = (f[dim - 1] - p) / N;
            p += del;
            varSum = (N - 2) * varSum / N + del * del;
            err = alph * Math.sqrt(varSum);
        }

        return p;
    }

    /**
     * Generate a random multivariate Gaussian sample.
     */
    public double[] rand() {
        double[] spt = new double[mu.length];

        for (int i = 0; i < mu.length; i++) {
            double u, v, q;
            do {
                u = Math.random();
                v = 1.7156 * (Math.random() - 0.5);
                double x = u - 0.449871;
                double y = Math.abs(v) + 0.386595;
                q = x * x + y * (0.19600 * y - 0.25472 * x);
            } while (q > 0.27597 && (q > 0.27846 || v * v > -4 * Math.log(u) * u * u));

            spt[i] = v / u;
        }

        double[] pt = new double[sigmaL.nrows()];

        // pt = sigmaL * spt
        for (int i = 0; i < pt.length; i++) {
            for (int j = 0; j <= i; j++) {
                pt[i] += sigmaL.get(i, j) * spt[j];
            }
        }

        Math.plus(pt, mu);

        return pt;
    }

    @Override
    public MultivariateMixture.Component M(double[][] x, double[] posteriori) {
        int n = x[0].length;

        double alpha = 0.0;
        double[] mean = new double[n];
        double[][] cov = new double[n][n];

        for (int k = 0; k < x.length; k++) {
            alpha += posteriori[k];
            for (int i = 0; i < n; i++) {
                mean[i] += x[k][i] * posteriori[k];
            }
        }

        for (int i = 0; i < mean.length; i++) {
            mean[i] /= alpha;
        }

        if (diagonal) {
            for (int k = 0; k < x.length; k++) {
                for (int i = 0; i < n; i++) {
                    cov[i][i] += (x[k][i] - mean[i]) * (x[k][i] - mean[i]) * posteriori[k];
                }
            }

            for (int i = 0; i < cov.length; i++) {
                cov[i][i] /= alpha;
            }
        } else {
            for (int k = 0; k < x.length; k++) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        cov[i][j] += (x[k][i] - mean[i]) * (x[k][j] - mean[j]) * posteriori[k];
                    }
                }
            }

            for (int i = 0; i < cov.length; i++) {
                for (int j = 0; j < cov[i].length; j++) {
                    cov[i][j] /= alpha;
                }

                // make sure the covariance matrix is positive definite.
                cov[i][i] *= 1.00001;
            }
        }

        MultivariateMixture.Component c = new MultivariateMixture.Component();
        c.priori = alpha;
        MultivariateGaussianDistribution g = new MultivariateGaussianDistribution(mean, cov);
        g.diagonal = diagonal;
        c.distribution = g;

        return c;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Multivariate Gaussian Distribution:\nmu = [");
        for (int i = 0; i < mu.length; i++) {
            builder.append(mu[i]).append(" ");
        }
        builder.setCharAt(builder.length() - 1, ']');
        builder.append("\nSigma = [\n");
        for (int i = 0; i < sigma.nrows(); i++) {
            builder.append('\t');
            for (int j = 0; j < sigma.ncols(); j++) {
                builder.append(sigma.get(i, j)).append(" ");
            }
            builder.append('\n');
        }
        builder.append("\t]");
        return builder.toString();
    }
}
