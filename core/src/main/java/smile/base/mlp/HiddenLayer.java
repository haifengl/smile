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
 * A hidden layer in the neural network.
 *
 * @author Haifeng Li
 */
public class HiddenLayer extends Layer {
    private static final long serialVersionUID = 2L;

    /** The activation function. */
    private final ActivationFunction activation;

    /**
     * Constructor.
     * @param n the number of neurons.
     * @param p the number of input variables (not including bias value).
     * @param activation the activation function.
     */
    public HiddenLayer(int n, int p, ActivationFunction activation) {
        super(n, p);
        this.activation = activation;
    }

    @Override
    public String toString() {
        if (dropout > 0.0) {
            return String.format("%s(%d, %.2f)", activation.name(), n, dropout);
        } else {
            return String.format("%s(%d)", activation.name(), n);
        }
    }

    @Override
    public void transform(double[] x) {
        activation.f(x);
    }

    @Override
    public void backpropagate(double[] lowerLayerGradient) {
        double[] output = this.output.get();
        double[] outputGradient = this.outputGradient.get();

        activation.g(outputGradient, output);
        if (lowerLayerGradient != null) {
            weight.tv(outputGradient, lowerLayerGradient);
        }
    }
}