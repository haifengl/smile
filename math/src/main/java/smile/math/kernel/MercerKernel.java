/*******************************************************************************
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
 ******************************************************************************/

package smile.math.kernel;

import java.io.Serializable;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;

/**
 * Mercer kernel, also called covariance function in Gaussian process.
 * A kernel is a continuous function that takes two variables x and y and
 * map them to a real value such that <code>k(x,y) = k(y,x)</code>.
 * A Mercer kernel is a kernel that is positive Semi-definite. When a kernel
 * is positive semi-definite, one may exploit the kernel trick, the idea of
 * implicitly mapping data to a high-dimensional feature space where some
 * linear algorithm is applied that works exclusively with inner products.
 * Assume we have some mapping &#934; from an input space X to a feature space H,
 * then a kernel <code>k(u, v) = &lt;&#934;(u), &#934;(v)&gt;</code> may be used
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
public interface MercerKernel<T> extends ToDoubleBiFunction<T,T>, Serializable {

    /**
     * Kernel function.
     */
    double k(T x, T y);

    /**
     * Kernel function.
     * This is simply for Scala convenience.
     */
    default double apply(T x, T y) {
        return k(x, y);
    }

    @Override
    default double applyAsDouble(T x, T y) {
        return k(x, y);
    }

    /**
     * The sum kernel takes two kernels and combines them via k1(x, y) + k2(x, y)
     * @param k1 the kernel to combine.
     * @param k2 the kernel to combine.
     * @return the sum kernel.
     */
    static <T> MercerKernel sum(MercerKernel<T> k1, MercerKernel<T> k2) {
        return new MercerKernel<T>() {
            @Override
            public double k(T x, T y) {
                return k1.k(x, y) + k2.k(x, y);
            }
        };
    }

    /**
     * The product kernel takes two kernels and combines them via k1(x, y) * k2(x, y)
     * . The Product kernel takes two kernels
     *  and
     *  and combines them via
     * . The Exponentiation kernel takes one base kernel and a scalar parameter  and combines them via
     * @param k1 the kernel to combine.
     * @param k2 the kernel to combine.
     * @return the product kernel.
     */
    static <T> MercerKernel product(MercerKernel<T> k1, MercerKernel<T> k2) {
        return new MercerKernel<T>() {
            @Override
            public double k(T x, T y) {
                return k1.k(x, y) + k2.k(x, y);
            }
        };
    }

    /**
     * The pow kernel takes one base kernel and a scalar parameter
     * and combines them via k(x, y) ^ p.
     *
     * @param k the base kernel.
     * @param p the exponent.
     * @return the power kernel.
     */
    static <T> MercerKernel pow(MercerKernel<T> k, double p) {
        return new MercerKernel<T>() {
            @Override
            public double k(T x, T y) {
                return Math.pow(k.k(x, y), p);
            }
        };
    }

    /**
     * Returns the kernel matrix.
     *
     * @param x samples.
     * @return the kernel matrix.
     */
    default Matrix K(T[] x) {
        int n = x.length;
        Matrix K = new Matrix(n, n);
        IntStream.range(0, n).parallel().forEach(i -> {
            T xi = x[i];
            for (int j = 0; j < n; j++) {
                K.set(i, j, k(xi, x[j]));
            }
        });

        K.uplo(UPLO.LOWER);
        return K;
    }

    /**
     * Returns the kernel matrix.
     *
     * @param x samples.
     * @param y samples.
     * @return the kernel matrix.
     */
    default Matrix K(T[] x, T[] y) {
        int m = x.length;
        int n = y.length;
        Matrix K = new Matrix(m, n);
        IntStream.range(0, m).parallel().forEach(i -> {
            T xi = x[i];
            for (int j = 0; j < n; j++) {
                K.set(i, j, k(xi, y[j]));
            }
        });

        return K;
    }
}
