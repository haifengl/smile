/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
package smile.vision.layer;

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
public class SqueezeExcitation extends LayerBlock {
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
        super("SqueezeExcitation");
        this.avgpool = new AdaptiveAvgPool2dLayer(1);
        this.conv1 = Layer.conv2d(inputChannels, squeezeChannels, 1);
        this.conv2 = Layer.conv2d(squeezeChannels, inputChannels, 1);
        this.delta = delta;
        this.sigma = sigma;
        add("avgpool", avgpool);
        add("fc1", conv1);
        add("fc2", conv2);
        add("activation", delta);
        add("scale_activation", sigma);
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor t1 = avgpool.forward(input);
        Tensor t2 = conv1.forward(t1);
        t1.close();

        t2 = delta.forward(t2);
        Tensor t3 = conv2.forward(t2);
        t2.close();

        t3 = sigma.forward(t3);
        Tensor output = t3.mul(input);
        t3.close();
        input.close();

        return output;
    }
}
