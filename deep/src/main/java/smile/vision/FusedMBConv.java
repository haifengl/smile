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
import smile.deep.layer.BatchNorm2dLayer;
import smile.deep.layer.Layer;
import smile.deep.layer.LayerBlock;
import smile.deep.tensor.Tensor;

/**
 * Fused-MBConv replaces the depthwise-conv3×3 and expansion-conv1×1
 * in MBConv with single regular conv3×3.
 *
 * @author Haifeng Li
 */
public class FusedMBConv implements Layer {
    private final Conv2dNormActivation expand;
    private final Conv2dNormActivation project;
    private final StochasticDepth stochasticDepth;
    private final boolean useResidual;

    /**
     * Constructor.
     * @param config block configuration.
     * @param stochasticDepthProb the probability of the input to be zeroed
     *                           in stochastic depth layer.
     */
    public FusedMBConv(MBConvConfig config, double stochasticDepthProb) {
        int stride = config.stride();
        if (stride < 1 || stride > 2) {
            throw new IllegalArgumentException("Illegal stride value: " + stride);
        }

        // expand
        int expandedChannels = MBConvConfig.adjustChannels(config.inputChannels(), config.expandRatio());
        if (expandedChannels == config.inputChannels()) {
            expand = new Conv2dNormActivation(
                    Layer.conv2d(config.inputChannels(), config.outputChannels(), config.kernel(), config.stride(), -1, 1, 1, true),
                    new BatchNorm2dLayer(expandedChannels),
                    new SiLU(true));
            project = null;
        } else {
            // fused expand
            expand = new Conv2dNormActivation(
                    Layer.conv2d(config.inputChannels(), expandedChannels, config.kernel(), config.stride(), -1, 1, 1, true),
                    new BatchNorm2dLayer(expandedChannels),
                    new SiLU(true));

            // project
            project = new Conv2dNormActivation(
                    Layer.conv2d(expandedChannels, config.outputChannels(), 1),
                    new BatchNorm2dLayer(config.outputChannels()), null);
        }

        useResidual = stride == 1 && config.inputChannels() == config.outputChannels();
        stochasticDepth = new StochasticDepth(stochasticDepthProb, "row");
    }

    @Override
    public void register(String name, LayerBlock block) {
        expand.register(name + "-expand", block);
        if (project != null) {
            project.register(name + "-project", block);
        }
        stochasticDepth.register(name + "-stochastic-depth", block);
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor output = expand.forward(input);
        if (project != null) {
            output = project.forward(output);
        }
        if (useResidual) {
            output = stochasticDepth.forward(output);
            output.add_(input);
        }
        return output;
    }
}
