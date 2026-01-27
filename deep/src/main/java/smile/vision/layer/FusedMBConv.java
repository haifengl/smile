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
package smile.vision.layer;

import java.util.function.IntFunction;
import smile.deep.activation.SiLU;
import smile.deep.layer.Layer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.SequentialBlock;
import smile.deep.tensor.Tensor;

/**
 * Fused-MBConv replaces the depthwise-conv3×3 and expansion-conv1×1
 * in MBConv with single regular conv3×3.
 *
 * @author Haifeng Li
 */
public class FusedMBConv extends LayerBlock {
    private final SequentialBlock block = new SequentialBlock();
    private final StochasticDepth stochasticDepth;
    private final boolean useResidual;

    /**
     * Constructor.
     * @param config block configuration.
     * @param stochasticDepthProb the probability of the input to be zeroed
     *                           in stochastic depth layer.
     * @param normLayer the functor to create the normalization layer.
     */
    public FusedMBConv(MBConvConfig config, double stochasticDepthProb, IntFunction<Layer> normLayer) {
        super("FusedMBConv");
        int stride = config.stride();
        if (stride < 1 || stride > 2) {
            throw new IllegalArgumentException("Illegal stride value: " + stride);
        }

        // expand
        int expandedChannels = MBConvConfig.adjustChannels(config.inputChannels(), config.expandRatio());
        if (expandedChannels == config.inputChannels()) {
            Conv2dNormActivation expand = new Conv2dNormActivation(new Conv2dNormActivation.Options(
                    config.inputChannels(), config.outputChannels(), config.kernel(), config.stride(), normLayer, new SiLU(true)));
            block.add(expand);
        } else {
            // fused expand
            Conv2dNormActivation expand = new Conv2dNormActivation(new Conv2dNormActivation.Options(
                    config.inputChannels(), expandedChannels, config.kernel(), config.stride(), normLayer, new SiLU(true)));
            // project
            Conv2dNormActivation project = new Conv2dNormActivation(new Conv2dNormActivation.Options(
                    expandedChannels, config.outputChannels(), 1, normLayer, null));
            block.add(expand);
            block.add(project);
        }

        useResidual = stride == 1 && config.inputChannels() == config.outputChannels();
        stochasticDepth = new StochasticDepth(stochasticDepthProb, "row");

        add("block", block);
        add("stochastic_depth", stochasticDepth);
    }

    @Override
    public Tensor forward(Tensor input) {
        try (input) {
            Tensor output = block.forward(input);

            if (useResidual) {
                output = stochasticDepth.forward(output);
                output.add_(input);
            }

            return output;
        }
    }
}
