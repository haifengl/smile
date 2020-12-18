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

package smile.regression;

import java.util.Arrays;
import java.util.Properties;
import smile.math.BFGS;
import smile.math.DifferentiableMultivariateFunction;
import smile.math.MathEx;
import smile.math.kernel.MercerKernel;
import smile.math.matrix.Matrix;
import smile.stat.distribution.MultivariateGaussianDistribution;
import smile.util.Strings;

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
 * scales as O(n<sup>3</sup>). For large problems (e.g. {@code n > 10,000}) both
 * storing the Gram matrix and solving the associated linear systems are
 * prohibitive on modern workstations. An extensive range of proposals have
 * been suggested to deal with this problem. A popular approach is the
 * reduced-rank Approximations of the Gram Matrix, known as Nystrom
 * approximation. Subset of Regressors (SR) is another popular approach
 * that uses an active set of training samples of size m selected from
 * the training set of size {@code n > m}. We assume that it is impossible
 * to search for the optimal subset of size m due to combinatorics.
 * The samples in the active set could be selected randomly, but in general
 * we might expect better performance if the samples are selected greedily
 * w.r.t. some criterion. Recently, researchers had proposed relaxing the
 * constraint that the inducing variables must be a subset of training/test
 * cases, turning the discrete selection problem into one of continuous
 * optimization.
 * <p>
 * Experimental evidence suggests that for large m the SR and Nystrom methods
 * have similar performance, but for small m the Nystrom method can be quite
 * poor. Also embarrassments can occur like the approximated predictive
 * variance being negative. For these reasons we do not recommend the
 * Nystrom method over the SR method.
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
public class GaussianProcessRegression<T> implements Regression<T> {
    private static final long serialVersionUID = 2L;

    /**
     * The covariance/kernel function.
     */
    public final MercerKernel<T> kernel;
    /**
     * The regressors.
     */
    public final T[] regressors;
    /**
     * The linear weights.
     */
    public final double[] w;
    /**
     * The mean of responsible variable.
     */
    public final double mean;
    /**
     * The standard deviation of responsible variable.
     */
    public final double sd;
    /**
     * The variance of noise.
     */
    public final double noise;
    /**
     * The log marginal likelihood, which may be not available (NaN) when the model
     * is fit with approximate methods.
     */
    public final double L;
    /**
     * The Cholesky decomposition of kernel matrix.
     */
    private final Matrix.Cholesky cholesky;

    /** The joint prediction of multiple data points. */
    public class JointPrediction {
        /** The query points where the GP is evaluated. */
        public final T[] x;
        /** The mean of predictive distribution at query points. */
        public final double[] mu;
        /** The standard deviation of predictive distribution at query points. */
        public final double[] sd;
        /** The covariance matrix of joint predictive distribution at query points. */
        public final Matrix cov;
        /** The joint predictive distribution. */
        private MultivariateGaussianDistribution dist;

        /**
         * Constructor.
         * @param x the query points.
         * @param mu the mean of predictive distribution at query points.
         * @param sd the standard deviation of predictive distribution at query points.
         * @param cov the covariance matrix of joint predictive distribution at query points.
         */
        public JointPrediction(T[] x, double[] mu, double[] sd, Matrix cov) {
            this.x = x;
            this.mu = mu;
            this.sd = sd;
            this.cov = cov;
        }

        /**
         * Draw samples from Gaussian process.
         * @param n The number of samples drawn from the Gaussian process.
         * @return n samples drawn from Gaussian process.
         */
        public double[][] sample(int n) {
            if (dist == null) {
                dist = new MultivariateGaussianDistribution(mu, cov);
            }

            return dist.rand(n);
        }

        @Override
        public String toString() {
            return String.format("GaussianProcessRegression.Prediction {\n  mean    = %s\n  std.dev = %s\n  cov     = %s\n}",
                    Strings.toString(mu), Strings.toString(sd), cov.toString(true));
        }
    }

    /**
     * Constructor.
     * @param kernel Kernel function.
     * @param regressors The regressors.
     * @param weight The weights of regressors.
     * @param noise The variance of noise.
     */
    public GaussianProcessRegression(MercerKernel<T> kernel, T[] regressors, double[] weight, double noise) {
        this(kernel, regressors, weight, noise, 0.0, 1.0);
    }

    /**
     * Constructor.
     * @param kernel Kernel function.
     * @param regressors The regressors.
     * @param weight The weights of regressors.
     * @param noise The variance of noise.
     * @param mean The mean of responsible variable.
     * @param sd The standard deviation of responsible variable.
     */
    public GaussianProcessRegression(MercerKernel<T> kernel, T[] regressors, double[] weight, double noise, double mean, double sd) {
        this(kernel, regressors, weight, noise, mean, sd, null, Double.NaN);
    }

    /**
     * Constructor.
     * @param kernel Kernel function.
     * @param regressors The regressors.
     * @param weight The weights of regressors.
     * @param noise The variance of noise.
     * @param mean The mean of responsible variable.
     * @param sd The standard deviation of responsible variable.
     * @param cholesky The Cholesky decomposition of kernel matrix.
     * @param L The log marginal likelihood.
     */
    public GaussianProcessRegression(MercerKernel<T> kernel, T[] regressors, double[] weight, double noise, double mean, double sd, Matrix.Cholesky cholesky, double L) {
        if (noise < 0.0) {
            throw new IllegalArgumentException("Invalid noise variance: " + noise);
        }

        this.kernel = kernel;
        this.regressors = regressors;
        this.w = weight;
        this.noise = noise;
        this.mean = mean;
        this.sd = sd;
        this.cholesky = cholesky;
        this.L = L;
    }

    @Override
    public double predict(T x) {
        int n = regressors.length;
        double mu = 0.0;

        for (int i = 0; i < n; i++) {
            mu += w[i] * kernel.k(x, regressors[i]);
        }

        return mu * sd + mean;
    }

    /**
     * Predicts the mean and standard deviation of an instance.
     * @param x an instance.
     * @param estimation an output array of the estimated mean and standard deviation.
     * @return the estimated mean value.
     */
    public double predict(T x, double[] estimation) {
        if (cholesky == null) {
            throw new UnsupportedOperationException("The Cholesky decomposition of kernel matrix is not available.");
        }

        int n = regressors.length;
        double[] k = new double[n];
        for (int i = 0; i < n; i++) {
            k[i] = kernel.k(x, regressors[i]);
        }

        double[] Kx = cholesky.solve(k);
        double mu = MathEx.dot(w, k);
        double sd = Math.sqrt(kernel.k(x, x) - MathEx.dot(Kx, k));

        mu = mu * this.sd + this.mean;
        sd *= this.sd;

        estimation[0] = mu;
        estimation[1] = sd;

        return mu;
    }

    /**
     * Evaluates the Gaussian Process at some query points.
     * @param samples query points.
     * @return The mean, standard deviation and covariances of GP at query points.
     */
    public JointPrediction query(T[] samples) {
        if (cholesky == null) {
            throw new UnsupportedOperationException("The Cholesky decomposition of kernel matrix is not available.");
        }

        Matrix Kx = kernel.K(samples);
        Matrix Kt = kernel.K(samples, regressors);

        Matrix Kv = Kt.transpose().clone();
        cholesky.solve(Kv);
        Matrix cov = Kx.sub(Kt.mm(Kv));
        cov.mul(sd * sd);

        double[] mu = Kt.mv(w);
        double[] std = cov.diag();
        int m = samples.length;
        for (int i = 0; i < m; i++) {
            mu[i] = mu[i] * sd + mean;
            std[i] = Math.sqrt(std[i]);
        }

        return new JointPrediction(samples, mu, std, cov);
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer("GaussianProcessRegression {\n");
        sb.append("  kernel: ").append(kernel).append(",\n");
        sb.append("  regressors: ").append(regressors.length).append(",\n");
        sb.append("  mean: ").append(String.format("%.4f,\n", mean));
        sb.append("  std.dev: ").append(String.format("%.4f,\n", sd));
        sb.append("  noise: ").append(String.format("%.4f", noise));
        if (!Double.isNaN(L)) {
            sb.append(",\n  log marginal likelihood: ").append(String.format("%.4f", L));
        }
        sb.append("\n}");
        return sb.toString();
    }

    /**
     * Fits a regular Gaussian process model.
     * @param x the training dataset.
     * @param y the response variable.
     * @param kernel the Mercer kernel.
     * @param prop the hyper-parameters.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> fit(T[] x, double[] y, MercerKernel<T> kernel, Properties prop) {
        double noise = Double.parseDouble(prop.getProperty("smile.gaussian.process.noise", "1E-10"));
        boolean normalize = Boolean.parseBoolean(prop.getProperty("smile.gaussian.process.normalize", "true"));
        double tol = Double.parseDouble(prop.getProperty("smile.gaussian.process.tolerance", "1E-5"));
        int maxIter = Integer.parseInt(prop.getProperty("smile.gaussian.process.max.iterations", "0"));
        return fit(x, y, kernel, noise, normalize, tol, maxIter);
    }

    /**
     * Fits a regular Gaussian process model by the method of subset of regressors.
     * @param x the training dataset.
     * @param y the response variable.
     * @param kernel the Mercer kernel.
     * @param noise the noise variance, which also works as a regularization parameter.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> fit(T[] x, double[] y, MercerKernel<T> kernel, double noise) {
        return fit(x, y, kernel, noise,true,1E-5, 0);
    }

    /**
     * Fits a regular Gaussian process model.
     * @param x the training dataset.
     * @param y the response variable.
     * @param kernel the Mercer kernel.
     * @param noise the noise variance, which also works as a regularization parameter.
     * @param normalize the flag if normalize the response variable.
     * @param tol the stopping tolerance for HPO.
     * @param maxIter the maximum number of iterations for HPO. No HPO if {@code maxIter <= 0}.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> fit(T[] x, double[] y, MercerKernel<T> kernel, double noise, boolean normalize, double tol, int maxIter) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (noise < 0.0) {
            throw new IllegalArgumentException("Invalid noise variance = " + noise);
        }

        int n = x.length;
        double mean = 0.0;
        double sd = 1.0;
        if (normalize) {
            mean = MathEx.mean(y);
            sd = MathEx.sd(y);

            double[] target = new double[n];
            for (int i = 0; i < n; i++) {
                target[i] = (y[i] - mean) / sd;
            }
            y = target;
        }

        if (maxIter > 0) {
            LogMarginalLikelihood<T> objective = new LogMarginalLikelihood<>(x, y, kernel);
            double[] hp = kernel.hyperparameters();
            double[] lo = kernel.lo();
            double[] hi = kernel.hi();

            int m = lo.length;
            double[] params = Arrays.copyOf(hp, m + 1);
            double[] l = Arrays.copyOf(lo, m + 1);
            double[] u = Arrays.copyOf(hi, m + 1);
            params[m] = noise;
            l[m] = 1E-10;
            u[m] = 1E5;

            BFGS.minimize(objective, 5, params, l, u, tol, maxIter);
            kernel = kernel.of(params);
            noise = params[params.length - 1];
        }

        Matrix K = kernel.K(x);
        for (int i = 0; i < n; i++) {
            K.add(i, i, noise);
        }

        Matrix.Cholesky cholesky = K.cholesky(true);
        double[] w = cholesky.solve(y);

        double L = -0.5 * (MathEx.dot(y, w) + cholesky.logdet() + n * Math.log(2.0 * Math.PI));

        return new GaussianProcessRegression<>(kernel, x, w, noise, mean, sd, cholesky, L);
    }

    /**
     * Fits an approximate Gaussian process model by the method of subset of regressors.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input, which are pre-selected or inducing samples
     *          acting as active set of regressors. In simple case, these can
     *          be chosen randomly from the training set or as the centers of
     *          k-means clustering.
     * @param kernel the Mercer kernel.
     * @param prop the hyper-parameters.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> fit(T[] x, double[] y, T[] t, MercerKernel<T> kernel, Properties prop) {
        double noise = Double.parseDouble(prop.getProperty("smile.gaussian.process.noise", "1E-10"));
        boolean normalize = Boolean.parseBoolean(prop.getProperty("smile.gaussian.process.normalize", "true"));
        return fit(x, y, t, kernel, noise, normalize);
    }

    /**
     * Fits an approximate Gaussian process model by the method of subset of regressors.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input, which are pre-selected or inducing samples
     *          acting as active set of regressors. In simple case, these can
     *          be chosen randomly from the training set or as the centers of
     *          k-means clustering.
     * @param kernel the Mercer kernel.
     * @param noise the noise variance, which also works as a regularization parameter.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> fit(T[] x, double[] y, T[] t, MercerKernel<T> kernel, double noise) {
        return fit(x, y, t, kernel, noise, true);
    }

    /**
     * Fits an approximate Gaussian process model by the method of subset of regressors.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input, which are pre-selected or inducing samples
     *          acting as active set of regressors. In simple case, these can
     *          be chosen randomly from the training set or as the centers of
     *          k-means clustering.
     * @param kernel the Mercer kernel.
     * @param noise the noise variance, which also works as a regularization parameter.
     * @param normalize the option to normalize the response variable.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> fit(T[] x, double[] y, T[] t, MercerKernel<T> kernel, double noise, boolean normalize) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (noise < 0.0) {
            throw new IllegalArgumentException("Invalid noise variance = " + noise);
        }

        double mean = 0.0;
        double sd = 1.0;
        if (normalize) {
            mean = MathEx.mean(y);
            sd = MathEx.sd(y);

            int n = x.length;
            double[] target = new double[n];
            for (int i = 0; i < n; i++) {
                target[i] = (y[i] - mean) / sd;
            }
            y = target;
        }

        Matrix G = kernel.K(x, t);
        Matrix K = G.ata();
        Matrix Kt = kernel.K(t);
        K.add(noise, Kt);
        Matrix.LU lu = K.lu(true);
        double[] Gty = G.tv(y);
        double[] w = lu.solve(Gty);

        return new GaussianProcessRegression<>(kernel, t, w, noise, mean, sd);
    }

    /**
     * Fits an approximate Gaussian process model with Nystrom approximation of kernel matrix.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input, which are pre-selected for Nystrom approximation.
     * @param kernel the Mercer kernel.
     * @param prop the hyper-parameters.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> nystrom(T[] x, double[] y, T[] t, MercerKernel<T> kernel, Properties prop) {
        double noise = Double.parseDouble(prop.getProperty("smile.gaussian.process.noise", "1E-10"));
        boolean normalize = Boolean.parseBoolean(prop.getProperty("smile.gaussian.process.normalize", "true"));
        return nystrom(x, y, t, kernel, noise, normalize);
    }

    /**
     * Fits an approximate Gaussian process model with Nystrom approximation of kernel matrix.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input, which are pre-selected for Nystrom approximation.
     * @param kernel the Mercer kernel.
     * @param noise the noise variance, which also works as a regularization parameter.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> nystrom(T[] x, double[] y, T[] t, MercerKernel<T> kernel, double noise) {
        return nystrom(x, y, t, kernel, noise, true);
    }

    /**
     * Fits an approximate Gaussian process model with Nystrom approximation of kernel matrix.
     * @param x the training dataset.
     * @param y the response variable.
     * @param t the inducing input, which are pre-selected for Nystrom approximation.
     * @param kernel the Mercer kernel.
     * @param noise the noise variance, which also works as a regularization parameter.
     * @param normalize the option to normalize the response variable.
     * @param <T> the data type of samples.
     * @return the model.
     */
    public static <T> GaussianProcessRegression<T> nystrom(T[] x, double[] y, T[] t, MercerKernel<T> kernel, double noise, boolean normalize) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (noise < 0.0) {
            throw new IllegalArgumentException("Invalid noise variance = " + noise);
        }

        int n = x.length;
        int m = t.length;

        double mean = 0.0;
        double sd = 1.0;
        if (normalize) {
            mean = MathEx.mean(y);
            sd = MathEx.sd(y);

            double[] target = new double[n];
            for (int i = 0; i < n; i++) {
                target[i] = (y[i] - mean) / sd;
            }
            y = target;
        }

        Matrix E = kernel.K(x, t);
        Matrix W = kernel.K(t);
        Matrix.EVD eigen = W.eigen(false, true, true).sort();
        Matrix U = eigen.Vr;
        Matrix D = eigen.diag();
        for (int i = 0; i < m; i++) {
            D.set(i, i, 1.0 / Math.sqrt(D.get(i, i)));
        }

        Matrix UD = U.mm(D);
        Matrix UDUt = UD.mt(U);
        Matrix L = E.mm(UDUt);

        Matrix LtL = L.ata();
        for (int i = 0; i < m; i++) {
            LtL.add(i, i, noise);
        }

        Matrix.Cholesky chol = LtL.cholesky(true);
        Matrix invLtL = chol.inverse();
        Matrix Kinv = L.mm(invLtL).mt(L);

        double[] w = Kinv.tv(y);
        for (int i = 0; i < n; i++) {
            w[i] = (y[i] - w[i]) / noise;
        }

        return new GaussianProcessRegression<>(kernel, x, w, noise, mean, sd);
    }

    /** Log marginal likelihood as optimization objective function. */
    private static class LogMarginalLikelihood<T> implements DifferentiableMultivariateFunction {
        final T[] x;
        final double[] y;
        MercerKernel<T> kernel;

        public LogMarginalLikelihood(T[] x, double[] y, MercerKernel<T> kernel) {
            this.x = x;
            this.y = y;
            this.kernel = kernel;
        }

        @Override
        public double f(double[] params) {
            kernel = kernel.of(params);
            double noise = params[params.length - 1];

            Matrix K = kernel.K(x);
            int n = x.length;
            for (int i = 0; i < n; i++) {
                K.add(i, i, noise);
            }

            Matrix.Cholesky cholesky = K.cholesky(true);
            double[] w = cholesky.solve(y);

            double L = -0.5 * (MathEx.dot(y, w) + cholesky.logdet() + n * Math.log(2.0 * Math.PI));
            return -L;
        }

        @Override
        public double g(double[] params, double[] g) {
            kernel = kernel.of(params);
            double noise = params[params.length - 1];

            Matrix[] K = kernel.KG(x);
            Matrix Ky = K[0];

            int n = x.length;
            for (int i = 0; i < n; i++) {
                Ky.add(i, i, noise);
            }

            Matrix.Cholesky cholesky = Ky.cholesky(true);
            Matrix Kinv = cholesky.inverse();
            double[] w = Kinv.mv(y);

            g[g.length - 1] = -(MathEx.dot(w, w) - Kinv.trace()) / 2;
            for (int i = 1; i < g.length; i++) {
                Matrix Kg = K[i];
                double gi = Kg.xAx(w) -  Kinv.mm(Kg).trace();
                g[i-1] = -gi / 2;
            }

            double L = -0.5 * (MathEx.dot(y, w) + cholesky.logdet() + n * Math.log(2.0 * Math.PI));
            return -L;
        }
    }
}
