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

import smile.deep.Model;
import smile.deep.activation.SiLU;
import smile.deep.layer.*;

/**
 * EfficientNet is an image classification model family. It was first
 * described in EfficientNet: Rethinking Model Scaling for Convolutional
 * Neural Networks.
 *
 * @author Haifeng Li
 */
public class EfficientNet extends Model {
    /**
     * Constructor.
     * @param invertedResidualSetting the network structure.
     * @param dropout the dropout probability.
     */
    public EfficientNet(MBConvConfig[] invertedResidualSetting, double dropout) {
        this(invertedResidualSetting, dropout, 0.2, 1000, 1280);
    }

    /**
     * Constructor.
     * @param invertedResidualSetting the network structure.
     * @param dropout the dropout probability.
     * @param stochasticDepthProb the stochastic depth probability.
     * @param numClasses the number of classes.
     * @param lastChannel the number of channels on the penultimate layer.
     */
    public EfficientNet(MBConvConfig[] invertedResidualSetting, double dropout, double stochasticDepthProb, int numClasses, int lastChannel) {
        super(init(invertedResidualSetting, dropout, stochasticDepthProb, numClasses, lastChannel));
    }

    /**
     * Constructor.
     * @param invertedResidualSetting the network structure.
     * @param dropout the dropout probability.
     * @param stochasticDepthProb the stochastic depth probability.
     * @param numClasses the number of classes.
     * @param lastChannel the number of channels on the penultimate layer.
     */
    private static SequentialBlock init(MBConvConfig[] invertedResidualSetting, double dropout, double stochasticDepthProb, int numClasses, int lastChannel) {
        Layer[] layers = new Layer[invertedResidualSetting.length + 2];

        // building first layer
        int firstconvOutputChannels = invertedResidualSetting[0].inputChannels();
        layers[0] = new Conv2dNormActivation(
                Layer.conv2d(3, firstconvOutputChannels, 3, 2, -1, 1, 1, true),
                new BatchNorm2dLayer(firstconvOutputChannels),
                new SiLU(true));

        // building inverted residual blocks
        int totalStageBlocks = 0;
        for (var config : invertedResidualSetting) {
            totalStageBlocks += config.numLayers();
        }

        int stageBlockId = 0;
        for (int i = 1; i <= invertedResidualSetting.length; i++) {
            var config = invertedResidualSetting[i - 1];
            Layer[] stage = new Layer[config.numLayers()];
            for (int j = 0; j < stage.length; j++) {
                // overwrite info if not the first conv in the stage
                if (j > 0) {
                    config = new MBConvConfig(
                            config.expandRatio(),
                            config.kernel(),
                            1, // stride
                            config.outputChannels(),
                            config.outputChannels(),
                            config.numLayers(),
                            config.block()
                    );
                }
                // adjust stochastic depth probability based on the depth of the stage block
                double sdprob = stochasticDepthProb * stageBlockId / totalStageBlocks;
                stage[j] = config.block().equals("MBConv") ? new MBConv(config, sdprob) : new FusedMBConv(config, sdprob);
                stageBlockId++;
            }
            layers[i] = new SequentialBlock(stage);
        }

        // building last several layers
        int lastConvInputChannels = invertedResidualSetting[invertedResidualSetting.length-1].outputChannels();
        int lastConvOutputChannels = lastChannel > 0 ? lastChannel : 4 * lastConvInputChannels;
        layers[invertedResidualSetting.length + 1] = new Conv2dNormActivation(
                Layer.conv2d(lastConvInputChannels, firstconvOutputChannels, 1),
                new BatchNorm2dLayer(firstconvOutputChannels),
                new SiLU(true));

        return new SequentialBlock(
                new SequentialBlock(layers),
                new AdaptiveAvgPool2dLayer(1),
                new DropoutLayer(dropout, true),
                new FullyConnectedLayer(lastConvOutputChannels, numClasses));
    }

    /**
     * EfficientNet-V2 S (baseline) model configuration.
     * Produced by neural architecture search.
     */
    public static MBConvConfig[] V2S = {
            MBConvConfig.FusedMBConv(1,3,1,24,24,2),
            MBConvConfig.FusedMBConv(4,3,2,24,48,4),
            MBConvConfig.FusedMBConv(4,3,2,48,64,4),
            MBConvConfig.MBConv(4,3,2,64,128,6),
            MBConvConfig.MBConv(6,3,1,128,160,9),
            MBConvConfig.MBConv(6,3,2,160,256,15)
    };

    /**
     * EfficientNet-V2 M (larger) model configuration based on compound
     * scaling method. Higher accuracy at the cost of increased latency.
     */
    public static MBConvConfig[] V2M = {
            MBConvConfig.FusedMBConv(1, 3, 1, 24, 24, 3),
            MBConvConfig.FusedMBConv(4, 3, 2, 24, 48, 5),
            MBConvConfig.FusedMBConv(4, 3, 2, 48, 80, 5),
            MBConvConfig.MBConv(4, 3, 2, 80, 160, 7),
            MBConvConfig.MBConv(6, 3, 1, 160, 176, 14),
            MBConvConfig.MBConv(6, 3, 2, 176, 304, 18),
            MBConvConfig.MBConv(6, 3, 1, 304, 512, 5)
    };

    /**
     * EfficientNet-V2 L (largest) model configuration based on compound
     * scaling method. Higher accuracy at the cost of increased latency.
     */
    public static MBConvConfig[] V2L = {
            MBConvConfig.FusedMBConv(1, 3, 1, 32, 32, 4),
            MBConvConfig.FusedMBConv(4, 3, 2, 32, 64, 7),
            MBConvConfig.FusedMBConv(4, 3, 2, 64, 96, 7),
            MBConvConfig.MBConv(4, 3, 2, 96, 192, 10),
            MBConvConfig.MBConv(6, 3, 1, 192, 224, 19),
            MBConvConfig.MBConv(6, 3, 2, 224, 384, 25),
            MBConvConfig.MBConv(6, 3, 1, 384, 640, 7)
    };
}
