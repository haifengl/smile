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

package smile.deep.activation;

/**
 * Hyperbolic tangent activation function. The tanh function is a
 * rescaling of the logistic sigmoid, such that its outputs range
 * from -1 to 1.
 *
 * @author Haifeng Li
 */
public class Tanh implements ActivationFunction {
    /** Default instance. */
    static Tanh instance = new Tanh();

    /**
     * Constructor.
     */
    public Tanh() {

    }

    @Override
    public String name() {
        return "Tanh";
    }

    @Override
    public void f(double[] x) {
        for (int i = 0; i < x.length; i++) {
            x[i] = Math.tanh(x[i]);
        }
    }

    @Override
    public void g(double[] g, double[] y) {
        int n = y.length;
        for (int i = 0; i < n; i++) {
            double ym1 = 1.0 - y[i];
            g[i] *= ym1 * ym1;
        }
    }
}
