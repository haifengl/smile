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

package smile.base.mlp;

import smile.math.MathEx;

import java.io.Serializable;

/**
 * The activation function in hidden layers.
 *
 * @author Haifeng Li
 */
public interface ActivationFunction extends Serializable {

    /** Returns the name of activation function. */
    String name();

    /**
     * The output function.
     * @param x the input vector.
     */
    void f(double[] x);

    /**
     * The gradient function.
     * @param g the gradient vector. On input, it holds W'*g, where W and g are the
     *          weight matrix and gradient of upper layer, respectively.
     *          On output, it is the gradient of this layer.
     * @param y the output vector.
     */
    void g(double[] g, double[] y);

    /**
     * Linear/Identity function.
     */
    static ActivationFunction linear() {
        return new ActivationFunction() {
            @Override
            public String name() {
                return "LINEAR";
            }

            @Override
            public void f(double[] x) {
                // Identity function keeps the input as is.
            }

            @Override
            public void g(double[] g, double[] y) {

            }
        };
    }

    /**
     * The rectifier activation function max(0, x). It is introduced
     * with strong biological motivations and mathematical justifications.
     * The rectifier is the most popular activation function for deep
     * neural networks. A unit employing the rectifier is called a
     * rectified linear unit (ReLU).
     */
    static ActivationFunction rectifier() {
        return new ActivationFunction() {
            @Override
            public String name() {
                return "RECTIFIER";
            }

            @Override
            public void f(double[] x) {
                for (int i = 0; i < x.length; i++) {
                    x[i] = Math.max(0.0, x[i]);
                }
            }

            @Override
            public void g(double[] g, double[] y) {
                for (int i = 0; i < g.length; i++) {
                    g[i] = y[i] > 0 ? 1 : 0;
                }

            }
        };
    }

    /**
     * Logistic sigmoid function: sigmoid(v)=1/(1+exp(-v)).
     * For multi-class classification, each unit in output layer
     * corresponds to a class. For binary classification and cross
     * entropy error function, there is only one output unit whose
     * value can be regarded as posteriori probability.
     */
    static ActivationFunction sigmoid() {
        return new ActivationFunction() {
            @Override
            public String name() {
                return "SIGMOID";
            }

            @Override
            public void f(double[] x) {
                for (int i = 0; i < x.length; i++) {
                    x[i] = MathEx.logistic(x[i]);
                }
            }

            @Override
            public void g(double[] g, double[] y) {
                for (int i = 0; i < g.length; i++) {
                    g[i] *= y[i] * (1.0 - y[i]);
                }
            }
        };
    }

    /**
     * Hyperbolic tangent activation function. The tanh function is a
     * rescaling of the logistic sigmoid, such that its outputs range
     * from -1 to 1.
     */
    static ActivationFunction tanh() {
        return new ActivationFunction() {
            @Override
            public String name() {
                return "TANH";
            }

            @Override
            public void f(double[] x) {
                for (int i = 0; i < x.length; i++) {
                    x[i] = MathEx.tanh(x[i]);
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
        };
    }

}
