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

package smile.base.svm;

import java.io.Serializable;
import smile.math.MathEx;
import smile.util.SparseArray;
import smile.math.kernel.BinarySparseLinearKernel;
import smile.math.kernel.LinearKernel;
import smile.math.kernel.SparseLinearKernel;

/** Linear kernel machine. */
public class LinearKernelMachine implements Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The weight vector.
     */
    private double[] w;
    /**
     * The intercept.
     */
    private double b = 0.0;

    /**
     * Constructor.
     *
     * @param w the weight vector.
     * @param b the intercept.
     */
    public LinearKernelMachine(double[] w, double b) {
        this.w = w;
        this.b = b;
    }

    /**
     * Creates a linear kernel machine.
     * @param kernelMachine a generic kernel machine.
     * @return a linear kernel machine
     */
    public static LinearKernelMachine of(KernelMachine<double[]> kernelMachine) {
        if (!(kernelMachine.kernel instanceof LinearKernel)) {
            throw new IllegalArgumentException("Not a linear kernel");
        }

        int n = kernelMachine.instances.length;
        int p = kernelMachine.instances[0].length;
        double[] w = new double[p];

        for (int i = 0; i < n; i++) {
            double alpha = kernelMachine.w[i];
            double[] x = kernelMachine.instances[i];
            for (int j = 0; j < p; j++) {
                w[j] += alpha * x[j];
            }
        }

        return new LinearKernelMachine(w, kernelMachine.b);
    }

    /**
     * Creates a linear kernel machine.
     * @param p the dimension of input vector.
     * @param kernelMachine a generic kernel machine.
     * @return a linear kernel machine
     */
    public static LinearKernelMachine binary(int p, KernelMachine<int[]> kernelMachine) {
        if (!(kernelMachine.kernel instanceof BinarySparseLinearKernel)) {
            throw new IllegalArgumentException("Not a linear kernel");
        }

        double[] w = new double[p];
        double[] alpha = kernelMachine.w;

        for (int[] x : kernelMachine.instances) {
            for (int i : x) {
                w[i] += alpha[i];
            }
        }

        return new LinearKernelMachine(w, kernelMachine.b);
    }

    /**
     * Creates a linear kernel machine.
     * @param p the dimension of input vector.
     * @param kernelMachine a generic kernel machine.
     * @return a linear kernel machine
     */
    public static LinearKernelMachine sparse(int p, KernelMachine<SparseArray> kernelMachine) {
        if (!(kernelMachine.kernel instanceof SparseLinearKernel)) {
            throw new IllegalArgumentException("Not a linear kernel");
        }

        double[] w = new double[p];
        double[] alpha = kernelMachine.w;

        for (SparseArray x : kernelMachine.instances) {
            for (SparseArray.Entry e : x) {
                w[e.i] += alpha[e.i] * e.x;
            }
        }

        return new LinearKernelMachine(w, kernelMachine.b);
    }

    /**
     * Returns the value of decision function.
     */
    public double f(double[] x) {
        return b + MathEx.dot(w, x);
    }

    /**
     * Returns the value of decision function.
     */
    public double f(int[] x) {
        double f = b;
        for (int i : x) {
            f += w[i];
        }

        return f;
    }

    /**
     * Returns the value of decision function.
     */
    public double f(SparseArray x) {
        double f = b;
        for (SparseArray.Entry e : x) {
            f += w[e.i] * e.x;
        }

        return f;
    }
}
