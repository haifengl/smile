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

import java.util.ArrayList;
import java.util.List;
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
public class SequentialBlock extends LayerBlock {
    private final List<Layer> layers = new ArrayList<>();

    /**
     * Constructor.
     */
    public SequentialBlock() {
        this("Sequential");
    }

    /**
     * Constructor.
     * @param name the module name.
     */
    public SequentialBlock(String name) {
        super(name);
    }

    /**
     * Constructor.
     * @param layers the neural network layers.
     */
    public SequentialBlock(Layer... layers) {
        super("Sequential");
        for (int i = 0; i < layers.length; i++) {
            this.layers.add(layers[i]);
            layers[i].register(Integer.toString(i), asTorch());
        }
    }

    /**
     * Adds a layer to the sequential block.
     * @param layer a layer.
     * @return this object.
     */
    public SequentialBlock add(Layer layer) {
        layer.register(Integer.toString(layers.size()), asTorch());
        layers.add(layer);
        return this;
    }

    @Override
    public Tensor forward(Tensor input) {
        ArrayList<Tensor> list = new ArrayList<>(layers.size());
        Tensor output = input;
        for (Layer layer : layers) {
            output = layer.forward(output);
            list.add(output);
        }

        if (!isTraining()) {
            for (var tensor : list) {
                if (tensor != output) {
                    tensor.close();
                }
            }
        }
        return output;
    }
}
