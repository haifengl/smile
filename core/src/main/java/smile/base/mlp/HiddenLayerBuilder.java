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
 * The builder of hidden layers.
 *
 * @author Haifeng Li
 */
public class HiddenLayerBuilder extends LayerBuilder {

    /** The activation function. */
    private final ActivationFunction activation;

    /**
     * Constructor.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     * @param activation the activation function.
     */
    public HiddenLayerBuilder(int neurons, double dropout, ActivationFunction activation) {
        super(neurons, dropout);
        this.activation = activation;
    }

    @Override
    public String toString() {
        return String.format("%s(%d)", activation.name(), neurons);
    }

    @Override
    public HiddenLayer build(int p) {
        return new HiddenLayer(neurons, p, activation);
    }
}
