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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model.mlp;

import smile.math.MathEx;
import smile.tensor.Vector;

import java.io.Serializable;

/**
 * The activation function in hidden layers.
 *
 * @author Haifeng Li
 */
public interface ActivationFunction extends Serializable {

    /**
     * Returns the name of activation function.
     * @return the name of activation function.
     */
    String name();

    /**
     * The output function.
     * @param x the input vector.
     */
    void f(Vector x);

    /**
     * The gradient function.
     * @param g the gradient vector. On input, it holds W'*g, where W and g are the
     *          weight matrix and gradient of upper layer, respectively.
     *          On output, it is the gradient of this layer.
     * @param y the output vector.
     */
    void g(Vector g, Vector y);

    /**
     * Linear/Identity activation function.
     *
     * @return the linear activation function.
     */
    static ActivationFunction linear() {
        return new ActivationFunction() {
            @Override
            public String name() {
                return "LINEAR";
            }

            @Override
            public void f(Vector x) {
                // Identity function keeps the input as is.
            }

            @Override
            public void g(Vector g, Vector y) {

            }
        };
    }

    /**
     * The rectifier activation function {@code max(0, x)}.
     * It is introduced with strong biological motivations and mathematical
     * justifications. The rectifier is the most popular activation function
     * for deep neural networks. A unit employing the rectifier is called a
     * rectified linear unit (ReLU).
     * <p>
     * ReLU neurons can sometimes be pushed into states in which they become
     * inactive for essentially all inputs. In this state, no gradients flow
     * backward through the neuron, and so the neuron becomes stuck in a
     * perpetually inactive state and "dies". This is a form of the vanishing
     * gradient problem. In some cases, large numbers of neurons in a network
     * can become stuck in dead states, effectively decreasing the model
     * capacity. This problem typically arises when the learning rate is
     * set too high. It may be mitigated by using leaky ReLUs instead,
     * which assign a small positive slope for {@code x < 0} however the
     * performance is reduced.
     *
     * @return the rectifier activation function.
     */
    static ActivationFunction rectifier() {
        return new ActivationFunction() {
            @Override
            public String name() {
                return "RECTIFIER";
            }

            @Override
            public void f(Vector x) {
                int n = x.size();
                for (int i = 0; i < n; i++) {
                    x.set(i, Math.max(0.0, x.get(i)));
                }
            }

            @Override
            public void g(Vector g, Vector y) {
                int n = y.size();
                for (int i = 0; i < n; i++) {
                    g.mul(i, y.get(i) > 0 ? 1 : 0);
                }
            }
        };
    }

    /**
     * The leaky rectifier activation function {@code max(x, 0.01x)}.
     *
     * @return the leaky rectifier activation function.
     */
    static ActivationFunction leaky() {
        return leaky(0.01);
    }

    /**
     * The leaky rectifier activation function {@code max(x, ax)} where
     * {@code 0 <= a < 1}. By default {@code a = 0.01}. Leaky ReLUs allow
     * a small, positive gradient when the unit is not active.
     * It has a relation to "maxout" networks.
     *
     * @param a the parameter of leaky ReLU.
     * @return the leaky rectifier activation function.
     */
    static ActivationFunction leaky(double a) {
        if (a < 0 || a >= 1.0) {
            throw new IllegalArgumentException("Invalid Leaky ReLU parameter: " + a);
        }

        return new ActivationFunction() {
            @Override
            public String name() {
                return String.format("LEAKEY_RECTIFIER(%f)", a);
            }

            @Override
            public void f(Vector x) {
                int n = x.size();
                for (int i = 0; i < n; i++) {
                    double xi = x.get(i);
                    x.set(i, Math.max(a * xi, xi));
                }
            }

            @Override
            public void g(Vector g, Vector y) {
                int n = y.size();
                for (int i = 0; i < n; i++) {
                    g.mul(i, y.get(i) > 0 ? 1 : a);
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
     *
     * @return the logistic sigmoid activation function.
     */
    static ActivationFunction sigmoid() {
        return new ActivationFunction() {
            @Override
            public String name() {
                return "SIGMOID";
            }

            @Override
            public void f(Vector x) {
                int n = x.size();
                for (int i = 0; i < n; i++) {
                    x.set(i, MathEx.sigmoid(x.get(i)));
                }
            }

            @Override
            public void g(Vector g, Vector y) {
                int n = y.size();
                for (int i = 0; i < n; i++) {
                    double yi =  y.get(i);
                    g.mul(i, yi * (1.0 - yi));
                }
            }
        };
    }

    /**
     * Hyperbolic tangent activation function. The tanh function is a
     * rescaling of the logistic sigmoid, such that its outputs range
     * from -1 to 1.
     *
     * @return the hyperbolic tangent activation function.
     */
    static ActivationFunction tanh() {
        return new ActivationFunction() {
            @Override
            public String name() {
                return "TANH";
            }

            @Override
            public void f(Vector x) {
                int n = x.size();
                for (int i = 0; i < n; i++) {
                    x.set(i, Math.tanh(x.get(i)));
                }
            }

            @Override
            public void g(Vector g, Vector y) {
                int n = y.size();
                for (int i = 0; i < n; i++) {
                    double ym1 = 1.0 - y.get(i);
                    g.mul(i, ym1 * ym1);
                }
            }
        };
    }

}
