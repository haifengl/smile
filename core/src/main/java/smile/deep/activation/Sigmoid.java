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
 * Logistic sigmoid function: sigmoid(v)=1/(1+exp(-v)).
 * For multi-class classification, each unit in output layer
 * corresponds to a class. For binary classification and cross
 * entropy error function, there is only one output unit whose
 * value can be regarded as posteriori probability.
 *
 * @author Haifeng Li
 */
public class Sigmoid implements ActivationFunction {
    /** Default instance. */
    static Sigmoid instance = new Sigmoid();

    /**
     * Constructor.
     */
    public Sigmoid() {

    }

    @Override
    public String name() {
        return "Sigmoid";
    }

    @Override
    public void f(double[] x) {
        for (int i = 0; i < x.length; i++) {
            x[i] = MathEx.sigmoid(x[i]);
        }
    }

    @Override
    public void g(double[] g, double[] y) {
        for (int i = 0; i < g.length; i++) {
            g[i] *= y[i] * (1.0 - y[i]);
        }
    }

}
