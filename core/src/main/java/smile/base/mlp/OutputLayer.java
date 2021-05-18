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

package smile.base.mlp;

/**
 * The output layer in the neural network.
 *
 * @author Haifeng Li
 */
public class OutputLayer extends Layer {
    private static final long serialVersionUID = 2L;

    /** The cost function. */
    private final Cost cost;
    /** The output activation function. */
    private final OutputFunction activation;

    /**
     * Constructor.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     * @param activation the output activation function.
     * @param cost the cost function.
     */
    public OutputLayer(int n, int p, OutputFunction activation, Cost cost) {
        super(n, p);

        switch (cost) {
            case MEAN_SQUARED_ERROR:
                if (activation == OutputFunction.SOFTMAX) {
                    throw new IllegalArgumentException("Softmax output function is not allowed with mean squared error cost function");
                }
                break;

            case LIKELIHOOD:
                if (activation == OutputFunction.LINEAR) {
                    throw new IllegalArgumentException("Linear output function is not allowed with likelihood cost function");
                }
                break;
        }

        this.activation = activation;
        this.cost = cost;
    }

    @Override
    public String toString() {
        return String.format("%s(%d) | %s", activation.name(), n, cost);
    }

    /**
     * Returns the cost function of neural network.
     * @return the cost function.
     */
    public Cost cost() {
        return cost;
    }

    @Override
    public void transform(double[] x) {
        activation.f(x);
    }

    @Override
    public void backpropagate(double[] lowerLayerGradient) {
        weight.tv(outputGradient.get(), lowerLayerGradient);
    }

    /**
     * Compute the network output gradient.
     * @param target the desired output.
     * @param weight a positive weight value associated with the training instance.
     */
    public void computeOutputGradient(double[] target, double weight) {
        double[] output = this.output.get();
        double[] outputGradient = this.outputGradient.get();

        int n = output.length;
        if (target.length != n) {
            throw new IllegalArgumentException(String.format("Invalid target vector size: %d, expected: %d", target.length, n));
        }

        for (int i = 0; i < n; i++) {
            outputGradient[i] = target[i] - output[i];
        }

        activation.g(cost, outputGradient, output);

        if (weight > 0.0 && weight != 1.0) {
            for (int i = 0; i < n; i++) {
                outputGradient[i] *= weight;
            }
        }
    }
}