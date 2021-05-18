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
 * The builder of output layers.
 *
 * @author Haifeng Li
 */
public class OutputLayerBuilder extends LayerBuilder {

    /** The output function. */
    private final OutputFunction output;
    /** The cost function. */
    private final Cost cost;

    /**
     * Constructor.
     * @param neurons the number of neurons.
     * @param output the output function.
     * @param cost the cost function.
     */
    public OutputLayerBuilder(int neurons, OutputFunction output, Cost cost) {
        super(neurons, 0.0); // no dropout in output layer
        this.output = output;
        this.cost = cost;
    }

    @Override
    public String toString() {
        return String.format("%s(%d) | %s", output.name(), neurons, cost);
    }

    @Override
    public OutputLayer build(int p) {
        return new OutputLayer(neurons, p, output, cost);
    }
}
