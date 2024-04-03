/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep.layer;

import org.bytedeco.pytorch.Module;

/**
 * A block is combinations of one or more layers. Blocks form the basis of
 * more complex network designs. LayerBlock allows treating the whole
 * container as a single layer, such that performing a transformation on
 * the LayerBlock applies to each of the layers it contains (which are each
 * a registered submodule of the block).
 *
 * @author Haifeng Li
 */
public interface LayerBlock extends Layer {
    /**
     * Returns the PyTorch Module object.
     * @return the PyTorch Module object.
     */
    Module asTorch();

    /**
     * Creates a sequential layer block.
     * @param layers the neural network layers.
     * @return the sequential layer block.
     */
    static SequentialBlock sequential(Layer... layers) {
        return new SequentialBlock(layers);
    }
}
