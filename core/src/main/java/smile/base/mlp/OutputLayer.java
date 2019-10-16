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

/**
 * The output layer in the neural network.
 */
public class OutputLayer extends Layer {
    private static final long serialVersionUID = 2L;

    /** The cost function. */
    private Cost cost;
    /** The output function. */
    private OutputFunction f;

    /**
     * Constructor.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     * @param f the output function.
     * @param cost the cost function.
     */
    public OutputLayer(int n, int p, OutputFunction f, Cost cost) {
        super(n, p);
        this.f = f;
        this.cost = cost;
        activation = f::f;
        output = new double[n];
        gradient = new double[n];
    }

    @Override
    public String toString() {
        return String.format("%s(%d) | %s", f.name(), n, cost);
    }

    /** Returns the cost function of neural network. */
    public Cost cost() {
        return cost;
    }

    @Override
    public void backpropagate(double[] error) {
        weight.atx(gradient, error);
    }

    /**
     * Compute the network output error.
     * @param target the desired output.
     * @param weight a positive weight value associated with the training instance.
     */
    public void computeError(double[] target, double weight) {
        int n = output.length;
        if (target.length != n) {
            throw new IllegalArgumentException(String.format("Invalid target vector size: %d, expected: %d", target.length, n));
        }

        for (int i = 0; i < n; i++) {
            gradient[i] = target[i] - output[i];
        }

        f.g(cost, gradient, output);

        if (weight > 0.0 && weight != 1.0) {
            for (int i = 0; i < n; i++) {
                gradient[i] *= weight;
            }
        }
    }

    /**
     * Returns an output layer with mean squared error cost function.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     * @param f the output function.
     */
    public static OutputLayer mse(int n, int p, OutputFunction f) {
        switch(f) {
            case SOFTMAX:
                throw new IllegalArgumentException("Softmax output function is not allowed with mean squared error cost function");
        }

        OutputLayer layer = new OutputLayer(n, p, f, Cost.MEAN_SQUARED_ERROR);
        return layer;
    }

    /**
     * Returns an output layer with (log-)likelihood cost function.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     * @param f the output function.
     */
    public static OutputLayer mle(int n, int p, OutputFunction f) {
        switch(f) {
            case LINEAR:
                throw new IllegalArgumentException("Linear output function is not allowed with likelihood cost function");
        }

        OutputLayer layer = new OutputLayer(n, p, f, Cost.LIKELIHOOD);
        return layer;
    }
}