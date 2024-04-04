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

import smile.deep.activation.SiLU;
import smile.deep.activation.Sigmoid;
import smile.deep.layer.BatchNorm2dLayer;
import smile.deep.layer.Layer;
import smile.deep.layer.LayerBlock;
import smile.deep.tensor.Tensor;

/**
 * Mobile inverted bottleneck convolution.
 *
 * MBConv = expansion-conv1x1 + depthwise-conv3x3 + SENet + conv1x1 + add
 *
 * @author Haifeng Li
 */
public class MBConv implements Layer {
    private final Conv2dNormActivation expand;
    private final Conv2dNormActivation depthwise;
    private final SqueezeExcitation se;
    private final Conv2dNormActivation project;
    private final StochasticDepth stochasticDepth;
    private final boolean useResidual;

    /**
     * Constructor.
     * @param config block configuration.
     * @param stochasticDepthProb the probability of the input to be zeroed
     *                           in stochastic depth layer.
     */
    public MBConv(MBConvConfig config, double stochasticDepthProb) {
        int stride = config.stride();
        if (stride < 1 || stride > 2) {
            throw new IllegalArgumentException("Illegal stride value: " + stride);
        }

        // expand
        int expandedChannels = MBConvConfig.adjustChannels(config.inputChannels(), config.expandRatio());
        if (expandedChannels == config.inputChannels()) {
            expand = null;
        } else {
            expand = new Conv2dNormActivation(
                            Layer.conv2d(config.inputChannels(), expandedChannels, 1),
                            new BatchNorm2dLayer(expandedChannels),
                            new SiLU(true));
        }

        // depthwise
        depthwise = new Conv2dNormActivation(
                Layer.conv2d(expandedChannels, expandedChannels, config.kernel(), config.stride(), -1, 1, expandedChannels, true),
                new BatchNorm2dLayer(expandedChannels),
                new SiLU(true));

        // squeeze and excitation
        int squeezeChannels = Math.max(1, config.inputChannels() / 4);
        se = new SqueezeExcitation(expandedChannels, squeezeChannels, new SiLU(true), new Sigmoid(true));

        // project
        project = new Conv2dNormActivation(
                Layer.conv2d(expandedChannels, config.outputChannels(), 1),
                new BatchNorm2dLayer(config.outputChannels()), null);

        useResidual = stride == 1 && config.inputChannels() == config.outputChannels();
        stochasticDepth = new StochasticDepth(stochasticDepthProb, "row");
    }

    @Override
    public void register(String name, LayerBlock block) {
        if (expand != null) {
            expand.register(name + "-expand", block);
        }
        depthwise.register(name + "-depthwise", block);
        se.register(name + "-se", block);
        project.register(name + "-project", block);
        stochasticDepth.register(name + "-stochastic-depth", block);
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor output = input;
        if (expand != null) {
            output = expand.forward(input);
        }
        output = depthwise.forward(output);
        output = se.forward(output);
        output = project.forward(output);
        if (useResidual) {
            output = stochasticDepth.forward(output);
            output.add_(input);
        }
        return output;
    }
}
