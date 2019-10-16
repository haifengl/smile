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
public class HiddenLayerBuilder {

    /** The activation function. */
    private ActivationFunction f;
    /** The number of neurons. */
    private int n;

    /**
     * Constructor.
     * @param n the number of neurons.
     * @param f the activation function.
     */
    public HiddenLayerBuilder(int n, ActivationFunction f) {
        this.n = n;
        this.f = f;
    }

    @Override
    public String toString() {
        return String.format("%s(%d)", f.name(), n);
    }

    /** Returns the number of neurons. */
    public int neurons() {
        return n;
    }

    /**
     * Creates a hidden layer.
     *
     * @param p the number of input variables (not including bias value).
     */
    public HiddenLayer build(int p) {
        return new HiddenLayer(n, p, f);
    }
}