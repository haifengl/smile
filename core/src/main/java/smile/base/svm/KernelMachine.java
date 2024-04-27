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

package smile.base.svm;

import java.io.Serial;
import java.io.Serializable;
import smile.math.kernel.MercerKernel;

/**
 * Kernel machines. Kernel methods owe their name to
 * the use of kernel functions, which enable them to operate in a high-dimensional,
 * implicit feature space without ever computing the coordinates of the data
 * in that space, but rather by simply computing the inner products between
 * the images of all pairs of data in the feature space.
 * <p>
 * Kernel methods can be thought of as instance-based learners: rather than
 * learning some fixed set of parameters corresponding to the features of
 * their inputs, they instead store (a subset of) their training set (or
 * a new representation) and learn for it a corresponding weight. Prediction
 * for unlabeled inputs is treated by the application of a similarity function.
 *
 * @param <T> the data type of model input objects.
 *
 * @author Haifeng Li
 */
public class KernelMachine<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The kernel function.
     */
    final MercerKernel<T> kernel;
    /**
     * The support vectors (or control points).
     */
    final T[] vectors;
    /**
     * The linear weights.
     */
    final double[] w;
    /**
     * The intercept.
     */
    final double b;

    /**
     * Constructor.
     * @param kernel Kernel function.
     * @param vectors The instances in the kernel machine, e.g. support vectors.
     * @param weight The weights of instances.
     */
    public KernelMachine(MercerKernel<T> kernel, T[] vectors, double[] weight) {
        this(kernel, vectors, weight, 0.0);
    }

    /**
     * Constructor.
     * @param kernel Kernel function.
     * @param vectors The instances in the kernel machine, e.g. support vectors.
     * @param weight The weights of instances.
     * @param b The intercept;
     */
    public KernelMachine(MercerKernel<T> kernel, T[] vectors, double[] weight, double b) {
        this.kernel = kernel;
        this.vectors = vectors;
        this.w = weight;
        this.b = b;
    }

    /**
     * Returns the kernel function.
     * @return the kernel function.
     */
    public MercerKernel<T> kernel() {
        return kernel;
    }

    /**
     * Returns the support vectors of kernel machines.
     * @return the support vectors of kernel machines.
     */
    public T[] vectors() {
        return vectors;
    }

    /**
     * Returns the weights of instances.
     * @return the weights of instances.
     */
    public double[] weights() {
        return w;
    }

    /**
     * Returns the intercept.
     * @return the intercept.
     */
    public double intercept() {
        return b;
    }

    /**
     * Returns the decision function value.
     * @param x an instance.
     * @return the decision function value.
     */
    public double score(T x) {
        double f = b;

        for (int i = 0; i < vectors.length; i++) {
            f += w[i] * kernel.k(x, vectors[i]);
        }

        return f;
    }

    @Override
    public String toString() {
        return String.format("Kernel Machine (%s): %d vectors, intercept = %.4f", kernel, vectors.length, b);
    }
}
