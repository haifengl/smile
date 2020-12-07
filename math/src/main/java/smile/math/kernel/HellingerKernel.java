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

/**
 * The Hellinger kernel. The Hellinger distance is used to quantify the
 * similarity between two probability distributions. It is a type of
 * f-divergence. The Hellinger distance H(P, Q) on discrete distributions
 * is equivalent to the Euclidean distance of the square root vectors.
 * The Hellinger kernel is 1 - H<sup>2</sup>(P, Q), which is equivalent
 * to the dot product of the square root vectors.
 *
 * @author Diego Catalano
 */
public class HellingerKernel implements MercerKernel<double[]> {
    private static final long serialVersionUID = 2L;
    
    /**
     * Constructor.
     */
    public HellingerKernel() {}

    @Override
    public String toString() {
        return "HellingerKernel()";
    }

    @Override
    public double k(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));
        }

        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum += Math.sqrt(x[i] * y[i]);
        }

        return sum;
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