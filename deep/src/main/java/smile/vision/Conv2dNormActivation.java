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
package smile.vision;

import org.bytedeco.pytorch.Module;
import smile.deep.activation.ActivationFunction;
import smile.deep.activation.ReLU;
import smile.deep.layer.BatchNorm2dLayer;
import smile.deep.layer.Conv2dLayer;
import smile.deep.layer.Layer;
import smile.deep.tensor.Tensor;

/**
 * Convolution2d-Normalization-Activation block.
 *
 * @author Haifeng Li
 */
public class Conv2dNormActivation implements Layer {
    private Module block = new Module();
    private final Conv2dLayer conv;
    private final BatchNorm2dLayer norm;
    private final ActivationFunction activation;

    /**
     * Constructor with default batch normalization and ReLU activation.
     * @param conv the convolutional layer.
     */
    public Conv2dNormActivation(Conv2dLayer conv) {
        this(conv, new BatchNorm2dLayer((int) conv.options().out_channels().get()), new ReLU(true));
    }

    /**
     * Constructor.
     * @param conv the convolutional layer.
     * @param norm the batch normalization layer.
     * @param activation the activation function.
     */
    public Conv2dNormActivation(Conv2dLayer conv, BatchNorm2dLayer norm, ActivationFunction activation) {
        this.conv = conv;
        this.norm = norm;
        this.activation = activation;
        conv.register("conv", block);
        norm.register("norm", block);
    }

    @Override
    public void register(String name, Module parent) {
        block = parent.register_module(name, block);
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor output = conv.forward(input);
        output = norm.forward(output);
        if (activation != null) {
            output = activation.apply(output);
        }
        return output;
    }
}
