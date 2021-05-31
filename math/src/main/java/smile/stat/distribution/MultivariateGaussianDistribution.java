/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.stat.distribution;

import java.util.Arrays;
import smile.math.MathEx;
import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;

/**
 * Multivariate Gaussian distribution.
 *
 * @see GaussianDistribution
 *
 * @author Haifeng Li
 */
public class MultivariateGaussianDistribution implements MultivariateDistribution, MultivariateExponentialFamily {
    private static final long serialVersionUID = 2L;

    private static final double LOG2PIE = Math.log(2 * Math.PI * Math.E);

    /** The mean vector. */
    public final double[] mu;
    /** The covariance matrix. */
    public final Matrix sigma;
    /** True if the covariance matrix is diagonal. */
    public final boolean diagonal;

    /** The dimension. */
    private int dim;
    /** The inverse of covariance matrix. */
    private Matrix sigmaInv;
    /** The Cholesky decomposition of covariance matrix. */
    private Matrix sigmaL;
    /** The determinant of covariance matrix. */
    private double sigmaDet;
    /** The constant factor in PDF. */
    private double pdfConstant;
    /** The number of parameters. */
    private final int length;

    /**
     * Constructor. The distribution will have a diagonal covariance matrix of
     * the same variance.
     *
     * @param mean mean vector.
     * @param variance variance.
     */
    public MultivariateGaussianDistribution(double[] mean, double variance) {
        if (variance <= 0) {
            throw new IllegalArgumentException("Variance is not positive: " + variance);
        }

        mu = mean;
        sigma = Matrix.diag(mu.length, variance);

        diagonal = true;
        length = mu.length + 1;

        init();
    }

    /**
     * Constructor. The distribution will have a diagonal covariance matrix.
     * Each element has different variance.
     *
     * @param mean mean vector.
     * @param variance variance vector.
     */
    public MultivariateGaussianDistribution(double[] mean, double[] variance) {
        if (mean.length != variance.length) {
            throw new IllegalArgumentException("Mean vector and covariance matrix have different dimension");
        }

        for (double v : variance) {
            if (v <= 0) {
                throw new IllegalArgumentException("Variance is not positive: " + v);
            }
        }

        mu = mean;
        sigma = Matrix.diag(variance);
        diagonal = true;
        length = 2 * mu.length;

        init();
    }

    /**
     * Constructor.
     *
     * @param mean mean vector.
     * @param cov covariance matrix.
     */
    public MultivariateGaussianDistribution(double[] mean, Matrix cov) {
        if (mean.length != cov.nrow()) {
            throw new IllegalArgumentException("Mean vector and covariance matrix have different dimension");
        }

        mu = mean;
        sigma = cov;

        diagonal = false;
        length = mu.length + mu.length * (mu.length + 1) / 2;

        init();
    }

    /**
     * Estimates the mean and diagonal covariance by MLE.
     * @param data the training data.
     * @return the distribution.
     */
    public static MultivariateGaussianDistribution fit(double[][] data) {
        return fit(data, false);
    }

    /**
     * Estimates the mean and covariance by MLE.
     * @param data the training data.
     * @param diagonal true if covariance matrix is diagonal.
     * @return the distribution.
     */
    public static MultivariateGaussianDistribution fit(double[][] data, boolean diagonal) {
        double[] mu = MathEx.colMeans(data);
        int n = data.length;
        int d = mu.length;

        if (diagonal) {
            double[] variance = new double[d];
            for (double[] x : data) {
                for (int j = 0; j < d; j++) {
                    variance[j] += (x[j] - mu[j]) * (x[j] - mu[j]);
                }
            }

            int n1 = n - 1;
            for (int j = 0; j < d; j++) {
                variance[j] /= n1;
            }

            return new MultivariateGaussianDistribution(mu, variance);
        } else {
            return new MultivariateGaussianDistribution(mu, Matrix.of(MathEx.cov(data, mu)));
        }
    }

    /**
     * Initialize the object.
     */
    private void init() {
        dim = mu.length;
        sigma.uplo(UPLO.LOWER);
        Matrix.Cholesky cholesky = sigma.cholesky();
        sigmaInv = cholesky.inverse();
        sigmaDet = cholesky.det();
        sigmaL = cholesky.lu;
        pdfConstant = (dim * Math.log(2 * Math.PI) + Math.log(sigmaDet)) / 2.0;
    }

    @Override
    public int length() {
        return length;
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
    public Matrix cov() {
        return sigma;
    }

    /**
     * Returns the scatter of distribution, which is defined as |&Sigma;|.
     * @return the scatter of distribution.
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
        MathEx.sub(v, mu);
        double result = sigmaInv.xAx(v) / -2.0;
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
        MathEx.sub(v, mu);

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
            double[] w = MathEx.random(dim - 1);
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
     * @return a random sample.
     */
    public double[] rand() {
        double[] spt = new double[mu.length];

        for (int i = 0; i < mu.length; i++) {
            double u, v, q;
            do {
                u = MathEx.random();
                v = 1.7156 * (MathEx.random() - 0.5);
                double x = u - 0.449871;
                double y = Math.abs(v) + 0.386595;
                q = x * x + y * (0.19600 * y - 0.25472 * x);
            } while (q > 0.27597 && (q > 0.27846 || v * v > -4 * Math.log(u) * u * u));

            spt[i] = v / u;
        }

        double[] pt = new double[sigmaL.nrow()];

        // pt = sigmaL * spt
        for (int i = 0; i < pt.length; i++) {
            for (int j = 0; j <= i; j++) {
                pt[i] += sigmaL.get(i, j) * spt[j];
            }
        }

        MathEx.add(pt, mu);

        return pt;
    }

    /**
     * Generates a set of random numbers following this distribution.
     * @param n the number of random samples to generate.
     * @return a set of random samples.
     */
    public double[][] rand(int n) {
        double[][] data = new double[n][];
        for (int i = 0; i < n; i++) {
            data[i] = rand();
        }
        return data;
    }

    @Override
    public MultivariateMixture.Component M(double[][] data, double[] posteriori) {
        int n = data.length;
        int d = data[0].length;

        double alpha = 0.0;
        double[] mean = new double[d];

        for (int k = 0; k < n; k++) {
            alpha += posteriori[k];
            double[] x = data[k];
            for (int i = 0; i < d; i++) {
                mean[i] += x[i] * posteriori[k];
            }
        }

        for (int i = 0; i < d; i++) {
            mean[i] /= alpha;
        }

        MultivariateGaussianDistribution gaussian;
        if (diagonal) {
            double[] variance = new double[d];
            for (int k = 0; k < n; k++) {
                double[] x = data[k];
                for (int i = 0; i < d; i++) {
                    variance[i] += (x[i] - mean[i]) * (x[i] - mean[i]) * posteriori[k];
                }
            }

            for (int i = 0; i < d; i++) {
                variance[i] /= alpha;
            }

            gaussian = new MultivariateGaussianDistribution(mean, variance);
        } else {
            Matrix cov = new Matrix(d, d);
            for (int k = 0; k < n; k++) {
                double[] x = data[k];
                for (int i = 0; i < d; i++) {
                    for (int j = 0; j < d; j++) {
                        cov.add(i, j, (x[i] - mean[i]) * (x[j] - mean[j]) * posteriori[k]);
                    }
                }
            }

            for (int i = 0; i < d; i++) {
                for (int j = 0; j < d; j++) {
                    cov.div(i, j, alpha);
                }

                // make sure the covariance matrix is positive definite.
                cov.mul(i, i, 1.00001);
            }

            gaussian = new MultivariateGaussianDistribution(mean, cov);
        }

        return new MultivariateMixture.Component(alpha, gaussian);
    }

    @Override
    public String toString() {
        return String.format("MultivariateGaussian(mu = %s, sigma = %s)", Arrays.toString(mu), sigma);
    }
}
