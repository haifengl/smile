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

import smile.math.MathEx;

/**
 * Softmax for multi-class cross entropy objection function.
 * The values of units in output layer can be regarded as
 * posteriori probabilities of each class.
 *
 * @author Haifeng Li
 */
public class Softmax implements ActivationFunction {
    /** Default instance. */
    static Softmax instance = new Softmax();

    /**
     * Constructor.
     */
    public Softmax() {

    }

    @Override
    public String name() {
        return "Softmax";
    }

    @Override
    public void f(double[] x) {
        MathEx.softmax(x);
    }

    @Override
    public void g(double[] g, double[] y) {
        for (int i = 0; i < g.length; i++) {
            g[i] *= y[i] > 0 ? 1 : 0;
        }
    }
}
