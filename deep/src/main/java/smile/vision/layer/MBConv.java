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

import java.util.function.IntFunction;
import smile.deep.activation.SiLU;
import smile.deep.activation.Sigmoid;
import smile.deep.layer.Layer;
import smile.deep.layer.LayerBlock;
import smile.deep.layer.SequentialBlock;
import smile.deep.tensor.Tensor;

/**
 * Mobile inverted bottleneck convolution.
 * <p>
 * MBConv = expansion-conv1x1 + depthwise-conv3x3 + SENet + conv1x1 + add
 *
 * @author Haifeng Li
 */
public class MBConv extends LayerBlock {
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
    public MBConv(MBConvConfig config, double stochasticDepthProb, IntFunction<Layer> normLayer) {
        super("MBConv");
        int stride = config.stride();
        if (stride < 1 || stride > 2) {
            throw new IllegalArgumentException("Illegal stride value: " + stride);
        }

        // expand
        int expandedChannels = MBConvConfig.adjustChannels(config.inputChannels(), config.expandRatio());
        if (expandedChannels != config.inputChannels()) {
            Conv2dNormActivation expand = new Conv2dNormActivation(new Conv2dNormActivation.Options(
                    config.inputChannels(), expandedChannels, 1, normLayer, new SiLU(true)));
            block.add(expand);
        }

        // depthwise
        Conv2dNormActivation depthwise = new Conv2dNormActivation(new Conv2dNormActivation.Options(
                expandedChannels, expandedChannels, config.kernel(), config.stride(),
                expandedChannels, normLayer, new SiLU(true)));
        block.add(depthwise);

        // squeeze and excitation
        int squeezeChannels = Math.max(1, config.inputChannels() / 4);
        SqueezeExcitation se = new SqueezeExcitation(expandedChannels, squeezeChannels, new SiLU(true), new Sigmoid(true));
        block.add(se);

        // project
        Conv2dNormActivation project = new Conv2dNormActivation(new Conv2dNormActivation.Options(
                expandedChannels, config.outputChannels(), 1, normLayer, null));
        block.add(project);

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
