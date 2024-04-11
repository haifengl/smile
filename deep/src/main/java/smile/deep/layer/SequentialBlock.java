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
import smile.deep.tensor.Tensor;

/**
 * A block of sequential layers. Layers will be added to it in the order
 * they are passed in the constructor. The forward() method of SequentialBlock
 * accepts any input and forwards it to the first layer it contains. It then
 * "chains" outputs to inputs sequentially for each subsequent module,
 * finally returning the output of the last layer.
 *
 * @author Haifeng Li
 */
public class SequentialBlock implements LayerBlock {
    final Layer[] layers;
    Module block = new Module();

    /**
     * Creates a sequential layer block.
     * @param layers the neural network layers.
     */
    public SequentialBlock(Layer... layers) {
        this.layers = layers;
        for (int i = 0; i < layers.length; i++) {
            layers[i].register(Integer.toString(i), this);
        }
    }

    @Override
    public Module asTorch() {
        return block;
    }

    @Override
    public void register(String name, Module parent) {
        this.block = parent.register_module(name, block);
    }

    @Override
    public Tensor forward(Tensor x) {
        for (Layer layer : layers) {
            x = layer.forward(x);
        }
        return x;
    }
}
