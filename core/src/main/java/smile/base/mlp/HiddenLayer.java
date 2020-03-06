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
    }

    @Override
    public String toString() {
        return String.format("%s(%d)", f.name(), n);
    }

    @Override
    public void f(double[] x) {
        f.f(x);
    }

    @Override
    public void backpropagate(double[] error) {
        f.g(gradient, output);
        if (error != null) {
            weight.atx(gradient, error);
        }
    }
}