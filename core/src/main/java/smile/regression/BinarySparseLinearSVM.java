/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression;

import smile.base.svm.KernelMachine;
import smile.base.svm.LinearKernelMachine;

/**
 * Binary sparse linear support vector machines for regression.
 *
 * @author Haifeng Li
 */
public class BinarySparseLinearSVM implements Regression<int[]> {
    /** The linear model. */
    private final LinearKernelMachine model;

    /**
     * Constructor.
     * @param p the dimension of input vector.
     * @param svm The kernel machine.
     */
    public BinarySparseLinearSVM(int p, KernelMachine<int[]> svm) {
        this.model = LinearKernelMachine.binary(p, svm);
    }

    /**
     * Returns the linear weights.
     * @return the linear weights.
     */
    public double[] weights() {
        return model.weights();
    }

    /**
     * Returns the intercept.
     * @return the intercept.
     */
    public double intercept() {
        return model.intercept();
    }

    @Override
    public double predict(int[] x) {
        return model.f(x) > 0 ? +1 : -1;
    }
}
