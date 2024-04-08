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

import smile.deep.activation.ActivationFunction;
import smile.deep.activation.ReLU;
import smile.deep.activation.Sigmoid;
import smile.deep.layer.*;
import smile.deep.tensor.Tensor;

/**
 * Squeeze-and-Excitation block from "Squeeze-and-Excitation Networks".
 *
 * @author Haifeng Li
 */
public class SqueezeExcitation implements Layer {
    private final AdaptiveAvgPool2dLayer avgpool;
    private final Conv2dLayer conv1, conv2;
    private final ActivationFunction delta, sigma;

    /**
     * Constructor.
     * @param inputChannels the number of channels in the input image.
     * @param squeezeChannels the number of squeeze channels.
     */
    public SqueezeExcitation(int inputChannels, int squeezeChannels) {
        this(inputChannels, squeezeChannels, new ReLU(true), new Sigmoid(true));
    }

    /**
     * Constructor.
     * @param inputChannels the number of channels in the input image.
     * @param squeezeChannels the number of squeeze channels.
     * @param delta the delta activation function.
     * @param sigma the sigma activation function.
     */
    public SqueezeExcitation(int inputChannels, int squeezeChannels, ActivationFunction delta, ActivationFunction sigma) {
        avgpool = new AdaptiveAvgPool2dLayer(1);
        conv1 = Layer.conv2d(inputChannels, squeezeChannels, 1);
        conv2 = Layer.conv2d(squeezeChannels, inputChannels, 1);
        this.delta = delta;
        this.sigma = sigma;
    }

    @Override
    public void register(String name, LayerBlock block) {
        avgpool.register(name + "-avgpool", block);
        conv1.register(name + "-conv1", block);
        conv2.register(name + "-conv2", block);
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor output = avgpool.forward(input);
        output = conv1.forward(output);
        output = delta.apply(output);
        output = conv2.forward(output);
        output = sigma.apply(output);
        output.mul_(input);
        return output;
    }
}