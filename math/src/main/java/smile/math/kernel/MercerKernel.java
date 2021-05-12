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

package smile.math.kernel;

import java.io.Serializable;
import java.util.Properties;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;
import smile.util.SparseArray;

/**
 * Mercer kernel, also called covariance function in Gaussian process.
 * A kernel is a continuous function that takes two variables x and y and
 * map them to a real value such that {@code k(x,y) = k(y,x)}.
 * A Mercer kernel is a kernel that is positive Semi-definite. When a kernel
 * is positive semi-definite, one may exploit the kernel trick, the idea of
 * implicitly mapping data to a high-dimensional feature space where some
 * linear algorithm is applied that works exclusively with inner products.
 * Assume we have some mapping &#934; from an input space X to a feature space H,
 * then a kernel {@code k(u, v) = <&#934;(u), &#934;(v)>} may be used
 * to define the inner product in feature space H.
 * <p>
 * Positive definiteness in the context of kernel functions also implies that
 * a kernel matrix created using a particular kernel is positive semi-definite.
 * A matrix is positive semi-definite if its associated eigenvalues are nonnegative.
 * <p>
 * We can combine or modify existing kernel functions to make new one.
 * For example, the sum of two kernels is a kernel. The product of two kernels
 * is also a kernel.
 * <p>
 * A stationary covariance function is a function of distance <code>x − y</code>.
 * Thus it is invariant stationarity to translations in the input space.
 * If further the covariance function is a function only of <code>|x − y|</code>
 * then it is called isotropic; it is thus invariant to all rigid motions.
 * If a covariance function depends only on the dot product of x and y,
 * we call it a dot product covariance function.
 *
 * @author Haifeng Li
 */
public interface MercerKernel<T> extends ToDoubleBiFunction<T, T>, Serializable {

    /**
     * Kernel function.
     * @param x an object.
     * @param y an object.
     * @return the kernel value.
     */
    double k(T x, T y);

    /**
     * Computes the kernel and its gradient over hyperparameters.
     * @param x an object.
     * @param y an object.
     * @return the kernel value and gradient.
     */
    double[] kg(T x, T y);

    /**
     * Kernel function.
     * This is simply for Scala convenience.
     * @param x an object.
     * @param y an object.
     * @return the kernel value.
     */
    default double apply(T x, T y) {
        return k(x, y);
    }

    @Override
    default double applyAsDouble(T x, T y) {
        return k(x, y);
    }

    /**
     * Computes the kernel and gradient matrices.
     *
     * @param x objects.
     * @return the kernel and gradient matrices.
     */
    default Matrix[] KG(T[] x) {
        int n = x.length;
        int m = lo().length;
        Matrix[] K = new Matrix[m + 1];
        for (int i = 0; i <= m; i++) {
            K[i] = new Matrix(n, n);
            K[i].uplo(UPLO.LOWER);
        }

        IntStream.range(0, n).parallel().forEach(j -> {
            T xj = x[j];
            for (int i = 0; i < n; i++) {
                double[] kg = kg(x[i], xj);
                for (int l = 0; l <= m; l++) {
                    K[l].set(i, j, kg[l]);
                }
            }
        });

        return K;
    }

    /**
     * Computes the kernel matrix.
     *
     * @param x objects.
     * @return the kernel matrix.
     */
    default Matrix K(T[] x) {
        int n = x.length;
        Matrix K = new Matrix(n, n);
        IntStream.range(0, n).parallel().forEach(j -> {
            T xj = x[j];
            for (int i = 0; i < n; i++) {
                K.set(i, j, k(x[i], xj));
            }
        });

        K.uplo(UPLO.LOWER);
        return K;
    }

    /**
     * Returns the kernel matrix.
     *
     * @param x objects.
     * @param y objects.
     * @return the kernel matrix.
     */
    default Matrix K(T[] x, T[] y) {
        int m = x.length;
        int n = y.length;
        Matrix K = new Matrix(m, n);
        IntStream.range(0, n).parallel().forEach(j -> {
            T yj = y[j];
            for (int i = 0; i < m; i++) {
                K.set(i, j, k(x[i], yj));
            }
        });

        return K;
    }

    /**
     * Returns the same kind kernel with the new hyperparameters.
     * @param params the hyperparameters.
     * @return the same kind kernel with the new hyperparameters.
     */
    MercerKernel<T> of(double[] params);

    /**
     * Returns the hyperparameters of kernel.
     * @return the hyperparameters of kernel.
     */
    double[] hyperparameters();

    /**
     * Returns the lower bound of hyperparameters (in hyperparameter tuning).
     * @return the lower bound of hyperparameters.
     */
    double[] lo();

    /**
     * Returns the upper bound of hyperparameters (in hyperparameter tuning).
     * @return the upper bound of hyperparameters.
     */
    double[] hi();

    /**
     * Returns a kernel instance.
     * @param prop the hyper-parameters.
     * @return the kernel.
     */
    static MercerKernel<double[]> of(Properties prop) {
        double scale = Double.parseDouble(prop.getProperty("kernel.scale", "1.0"));
        double offset = Double.parseDouble(prop.getProperty("kernel.offset", "0.0"));

        String kernel = prop.getProperty("kernel", "Gaussian").toLowerCase();
        switch (kernel) {
            case "linear":
                return new LinearKernel();
            case "gaussian":
                return new GaussianKernel(scale);
            case "polynomial":
                int degree = Integer.parseInt(prop.getProperty("kernel.degree", "2"));
                return new PolynomialKernel(degree, scale, offset);
            case "matern":
                double nu = Double.parseDouble(prop.getProperty("matern.nu", "1.5"));
                return new MaternKernel(scale, nu);
            case "laplacian":
                return new LaplacianKernel(scale);
            case "hyperbolic":
                return new HyperbolicTangentKernel(scale, offset);
            case "spline":
                return new ThinPlateSplineKernel(scale);
            case "pearson":
                String omega = prop.getProperty("pearson.omega");
                if (omega == null) {
                    throw new IllegalArgumentException("pearson.omega is not specified");
                }
                return new PearsonKernel(scale, Double.parseDouble(omega));
            case "hellinger":
                return new HellingerKernel();
            default:
                throw new IllegalArgumentException("Unsupported kernel: " + kernel);
        }
    }

    /**
     * Returns a sparse kernel instance.
     * @param prop the hyper-parameters.
     * @return the kernel.
     */
    static MercerKernel<SparseArray> sparse(Properties prop) {
        double scale = Double.parseDouble(prop.getProperty("kernel.scale", "1.0"));
        double offset = Double.parseDouble(prop.getProperty("kernel.offset", "0.0"));

        String kernel = prop.getProperty("kernel", "Gaussian").toLowerCase();
        switch (kernel) {
            case "linear":
                return new SparseLinearKernel();
            case "gaussian":
                return new SparseGaussianKernel(scale);
            case "polynomial":
                int degree = Integer.parseInt(prop.getProperty("kernel.degree", "2"));
                return new SparsePolynomialKernel(degree, scale, offset);
            case "matern":
                double nu = Double.parseDouble(prop.getProperty("matern.nu", "1.5"));
                return new SparseMaternKernel(scale, nu);
            case "laplacian":
                return new SparseLaplacianKernel(scale);
            case "hyperbolic":
                return new SparseHyperbolicTangentKernel(scale, offset);
            case "spline":
                return new SparseThinPlateSplineKernel(scale);
            default:
                throw new IllegalArgumentException("Unsupported kernel: " + kernel);
        }
    }

    /**
     * Returns a binary sparse kernel instance.
     * @param prop the hyper-parameters.
     * @return the kernel.
     */
    static MercerKernel<int[]> binary(Properties prop) {
        double scale = Double.parseDouble(prop.getProperty("kernel.scale", "1.0"));
        double offset = Double.parseDouble(prop.getProperty("kernel.offset", "0.0"));

        String kernel = prop.getProperty("kernel", "Gaussian").toLowerCase();
        switch (kernel) {
            case "linear":
                return new BinarySparseLinearKernel();
            case "gaussian":
                return new BinarySparseGaussianKernel(scale);
            case "polynomial":
                int degree = Integer.parseInt(prop.getProperty("kernel.degree", "2"));
                return new BinarySparsePolynomialKernel(degree, scale, offset);
            case "matern":
                double nu = Double.parseDouble(prop.getProperty("matern.nu", "1.5"));
                return new BinarySparseMaternKernel(scale, nu);
            case "laplacian":
                return new BinarySparseLaplacianKernel(scale);
            case "hyperbolic":
                return new BinarySparseHyperbolicTangentKernel(scale, offset);
            case "spline":
                return new BinarySparseThinPlateSplineKernel(scale);
            default:
                throw new IllegalArgumentException("Unsupported kernel: " + kernel);
        }
    }
}
