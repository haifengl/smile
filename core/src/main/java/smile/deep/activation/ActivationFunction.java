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

import java.io.Serializable;

/**
 * The activation function. An activation function defines how the weighted
 * sum of the input is transformed into an output from a node or nodes in
 * a layer of the network.
 * <p>
 * The choice of activation function has a large impact on the capability
 * and performance of the neural network, and different activation
 * functions may be used in different parts of the model although the same
 * activation function is used for all nodes in a layer.
 * <p>
 * There are many different types of activation functions, although perhaps
 * only a small number of functions used in practice.
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
     * Returns the rectifier activation function {@code max(0, x)}.
     *
     * @return the rectifier activation function.
     */
    static ReLU relu() {
        return ReLU.instance;
    }

    /**
     * Returns the leaky rectifier activation function {@code max(x, 0.01x)}.
     *
     * @return the leaky rectifier activation function.
     */
    static LeakyReLU leaky() {
        return LeakyReLU.instance;
    }

    /**
     * Returns the leaky rectifier activation function {@code max(x, ax)} where
     * {@code 0 <= a < 1}.
     *
     * @param a the parameter of leaky ReLU.
     * @return the leaky rectifier activation function.
     */
    static LeakyReLU leaky(double a) {
        return new LeakyReLU(a);
    }

    /**
     * Returns the logistic sigmoid function: sigmoid(v)=1/(1+exp(-v)).
     *
     * @return the logistic sigmoid activation function.
     */
    static Sigmoid sigmoid() {
        return Sigmoid.instance;
    }

    /**
     * Returns the hyperbolic tangent activation function.
     * The tanh function is a rescaling of the logistic sigmoid,
     * such that its outputs range from -1 to 1.
     *
     * @return the hyperbolic tangent activation function.
     */
    static Tanh tanh() {
        return Tanh.instance;
    }

    /**
     * Returns the softmax activation function for multi-class output layer.
     *
     * @return the softmax activation function.
     */
    static Softmax softmax() {
        return Softmax.instance;
    }
}
