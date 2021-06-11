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
 * The leaky rectifier activation function {@code max(x, ax)} where
 * {@code 0 <= a < 1}. By default {@code a = 0.01}. Leaky ReLUs allow
 * a small, positive gradient when the unit is not active.
 * It has a relation to "maxout" networks.
 *
 * @author Haifeng Li
 */
public class LeakyReLU implements ActivationFunction {
    /** Default instance. */
    static LeakyReLU instance = new LeakyReLU(0.01);

    private double a;
    /**
     * Constructor.
     */
    public LeakyReLU(double a) {
        if (a < 0 || a >= 1.0) {
            throw new IllegalArgumentException("Invalid Leaky ReLU parameter: " + a);
        }

        this.a = a;
    }

    @Override
    public String name() {
        return "LeakyReLU";
    }

    @Override
    public void f(double[] x) {
        for (int i = 0; i < x.length; i++) {
            x[i] = Math.max(a * x[i], x[i]);
        }
    }

    @Override
    public void g(double[] g, double[] y) {
        for (int i = 0; i < g.length; i++) {
            g[i] *= y[i] > 0 ? 1 : a;
        }
    }
}
