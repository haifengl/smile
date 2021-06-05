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
 * The builder of layers.
 *
 * @author Haifeng Li
 */
public abstract class LayerBuilder {

    /** The number of neurons. */
    protected final int neurons;
    /** The dropout rate. */
    protected final double dropout;

    /**
     * Constructor.
     * @param neurons the number of neurons.
     * @param dropout the dropout rate.
     */
    public LayerBuilder(int neurons, double dropout) {
        this.neurons = neurons;
        this.dropout = dropout;
    }

    /**
     * Returns the number of neurons.
     * @return the number of neurons.
     */
    public int neurons() {
        return neurons;
    }

    /**
     * Builds a layer.
     *
     * @param p the number of input variables (not including bias value).
     * @return a layer.
     */
    public abstract Layer build(int p);
}