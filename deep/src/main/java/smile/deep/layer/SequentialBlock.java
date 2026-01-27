/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep.layer;

import java.util.ArrayList;
import java.util.List;
import smile.deep.tensor.Tensor;
import smile.util.AutoScope;

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
        for (var layer : layers) {
            add(layer);
        }
    }

    /**
     * Adds a layer to the sequential block.
     * @param layer a layer.
     * @return this object.
     */
    public SequentialBlock add(Layer layer) {
        super.add(Integer.toString(layers.size()), layer);
        layers.add(layer);
        return this;
    }

    @Override
    public Tensor forward(Tensor input) {
        // We should not add input to scope as
        // it may be used in skip connections in ResNet.
        // That is, input + f(input).
        try (var scope = new AutoScope()) {
            Tensor output = input;
            for (var layer : layers) {
                output = layer.forward(input);
                if (output != input) scope.add(output);
                input = output;
            }
            scope.remove(output);
            return output;
        }
    }
}
