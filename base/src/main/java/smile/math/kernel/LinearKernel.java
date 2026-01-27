/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math.kernel;

import smile.math.MathEx;

import java.io.Serial;

/**
 * The linear dot product kernel. When using a linear kernel, input space is
 * identical to feature space.
 *
 * @author Haifeng Li
 */
public class LinearKernel implements MercerKernel<double[]>, DotProductKernel {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Constructor.
     */
    public LinearKernel() {

    }

    @Override
    public String toString() {
        return "LinearKernel()";
    }

    @Override
    public double k(double dot) {
        return dot;
    }

    @Override
    public double[] kg(double dot) {
        return new double[] { dot };
    }

    @Override
    public double k(double[] x, double[] y) {
        return MathEx.dot(x, y);
    }

    @Override
    public double[] kg(double[] x, double[] y) {
        return new double[] { k(x, y) };
    }

    @Override
    public LinearKernel of(double[] params) {
        return new LinearKernel();
    }

    @Override
    public double[] hyperparameters() {
        return new double[0];
    }

    @Override
    public double[] lo() {
        return new double[0];
    }

    @Override
    public double[] hi() {
        return new double[0];
    }
}
