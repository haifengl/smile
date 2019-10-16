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

/**
 * A hidden layer in the neural network.
 */
public class HiddenLayer extends Layer {
    private static final long serialVersionUID = 2L;

    /** The activation function. */
    private ActivationFunction f;

    /**
     * Constructor.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     * @param f the activation function.
     */
    public HiddenLayer(int n, int p, ActivationFunction f) {
        super(n, p);
        this.f = f;
        activation = f::f;
        output = new double[n + 1];
        gradient = new double[n + 1];
        output[n] = 1.0; // intercept
    }

    @Override
    public String toString() {
        return String.format("%s(%d)", f.name(), n);
    }

    @Override
    public void backpropagate(double[] error) {
        f.g(gradient, output);
        if (error != null) {
            weight.atx(gradient, error);
        }
    }

    /**
     * Returns a hidden layer with linear activation function.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public static HiddenLayer linear(int n, int p) {
        return new HiddenLayer(n, p, ActivationFunction.linear());
    }

    /**
     * Returns a hidden layer with rectified linear activation function.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public static HiddenLayer rectifier(int n, int p) {
        return new HiddenLayer(n, p, ActivationFunction.rectifier());
    }

    /**
     * Returns a hidden layer with sigmoid activation function.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public static HiddenLayer sigmoid(int n, int p) {
        return new HiddenLayer(n, p, ActivationFunction.sigmoid());
    }

    /**
     * Returns a hidden layer with hyperbolic tangent activation function.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     */
    public static HiddenLayer tanh(int n, int p) {
        return new HiddenLayer(n, p, ActivationFunction.tanh());
    }

    /**
     * Returns a hidden layer with linear activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder linear(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.linear());
    }

    /**
     * Returns a hidden layer with rectified linear activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder rectifier(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.rectifier());
    }

    /**
     * Returns a hidden layer with sigmoid activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder sigmoid(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.sigmoid());
    }

    /**
     * Returns a hidden layer with hyperbolic tangent activation function.
     * @param n the number of neurons.
     */
    public static HiddenLayerBuilder tanh(int n) {
        return new HiddenLayerBuilder(n, ActivationFunction.tanh());
    }
}