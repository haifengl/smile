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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.vision;

import java.awt.Image;
import java.util.function.IntFunction;
import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.global.torch;
import smile.deep.activation.SiLU;
import smile.deep.layer.*;
import smile.deep.tensor.Tensor;
import smile.vision.layer.*;
import smile.vision.transform.Transform;

/**
 * EfficientNet is an image classification model family. It was first
 * described in EfficientNet: Rethinking Model Scaling for Convolutional
 * Neural Networks.
 *
 * @author Haifeng Li
 */
public class EfficientNet extends LayerBlock {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EfficientNet.class);
    private final AdaptiveAvgPool2dLayer avgpool;
    private final SequentialBlock features;
    private final SequentialBlock classifier;

    /**
     * Constructor.
     * @param invertedResidualSetting the network structure.
     * @param dropout the dropout probability.
     * @param stochasticDepthProb the stochastic depth probability.
     * @param numClasses the number of classes.
     * @param lastChannel the number of channels on the penultimate layer.
     * @param normLayer the functor to create the normalization layer.
     */
    public EfficientNet(MBConvConfig[] invertedResidualSetting, double dropout, double stochasticDepthProb,
                        int numClasses, int lastChannel, IntFunction<Layer> normLayer) {
        super("EfficientNet");

        if (normLayer == null) {
            normLayer = BatchNorm2dLayer::new;
        }

        Layer[] layers = new Layer[invertedResidualSetting.length + 2];
        // building first layer
        int firstconvOutputChannels = invertedResidualSetting[0].inputChannels();
        layers[0] = new Conv2dNormActivation(new Conv2dNormActivation.Options(
                3, firstconvOutputChannels, 3, 2, normLayer, new SiLU(true)));

        // building inverted residual blocks
        int totalStageBlocks = 0;
        for (var config : invertedResidualSetting) {
            totalStageBlocks += config.numLayers();
        }

        int stageBlockId = 0;
        for (int i = 1; i <= invertedResidualSetting.length; i++) {
            var config = invertedResidualSetting[i - 1];
            logger.debug("Layer {}: {}", i, config);
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
                logger.debug("Stage {} BlockId {}: {} sdprob = {}", j, stageBlockId, config, sdprob);
                stage[j] = config.block().equals("MBConv") ? new MBConv(config, sdprob, normLayer) : new FusedMBConv(config, sdprob, normLayer);
                stageBlockId++;
            }
            layers[i] = new SequentialBlock(stage);
        }

        // building last several layers
        int lastConvInputChannels = invertedResidualSetting[invertedResidualSetting.length-1].outputChannels();
        if (lastChannel <= 0) {
            lastChannel = 4 * lastConvInputChannels;
        }
        layers[invertedResidualSetting.length + 1] = new Conv2dNormActivation(new Conv2dNormActivation.Options(
                lastConvInputChannels, lastChannel, 1, normLayer, new SiLU(true)));

        features = new SequentialBlock(layers);
        avgpool = new AdaptiveAvgPool2dLayer(1);
        classifier = new SequentialBlock(
                new DropoutLayer(dropout, true),
                new LinearLayer(lastChannel, numClasses));
        add("features", features);
        add("avgpool", avgpool);
        add("classifier", classifier);

        // Initialization should run in torch.no_grad() mode and
        // will not be taken into account by autograd.
        try (var guard = Tensor.noGradGuard()) {
            try (var modules = module.modules()) {
                for (int i = 0; i < modules.size(); i++) {
                    var module = modules.get(i);
                    var name = module.name().getString();
                    switch (name) {
                        case "torch::nn::Conv2dImpl":
                            var conv2d = module.asConv2d();
                            torch.kaiming_normal_(conv2d.weight(), 0.0, new FanModeType(new kFanOut()), new Nonlinearity(new kLeakyReLU()));
                            var bias = conv2d.bias();
                            if (bias.defined()) {
                                bias.zero_();
                            }
                            break;
                        case "torch::nn::BatchNorm2dImpl":
                            var batchNorm2d = module.asBatchNorm2d();
                            torch.ones_(batchNorm2d.weight());
                            batchNorm2d.bias().zero_();
                            break;
                        case "torch::nn::GroupNormImpl":
                            var groupNorm = module.asGroupNorm();
                            torch.ones_(groupNorm.weight());
                            groupNorm.bias().zero_();
                            break;
                        case "torch::nn::LinearImpl":
                            var linear = module.asLinear();
                            double range = 1.0 / Math.sqrt(linear.options().out_features().get());
                            torch.uniform_(linear.weight(), -range, range);
                            torch.zeros_(linear.bias());
                            break;
                    }
                }
            }
        }
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor t1 = features.forward(input);
        Tensor t2 = avgpool.forward(t1);
        t1.close();
        Tensor t3 = t2.flatten(1);
        Tensor output = classifier.forward(t3);
        t2.close();
        t3.close();
        return output;
    }

    /**
     * Returns the feature layer block.
     * @return the feature layer block.
     */
    public SequentialBlock features() {
        return features;
    }

    /**
     * EfficientNet-V2_S (baseline) model.
     * @return the model.
     */
    public static VisionModel V2S() {
        return V2S("model/EfficientNet/efficientnet_v2_s.pt");
    }

    /**
     * EfficientNet-V2_S (baseline) model.
     * @param path the pre-trained model file path.
     * @return the model.
     */
    public static VisionModel V2S(String path) {
        MBConvConfig[] config = {
                MBConvConfig.FusedMBConv(1,3,1,24,24,2),
                MBConvConfig.FusedMBConv(4,3,2,24,48,4),
                MBConvConfig.FusedMBConv(4,3,2,48,64,4),
                MBConvConfig.MBConv(4,3,2,64,128,6),
                MBConvConfig.MBConv(6,3,1,128,160,9),
                MBConvConfig.MBConv(6,3,2,160,256,15)
        };
        Transform transform = Transform.classification(384, 384);

        var net = new EfficientNet(config, 0.2, 0.2, 1000, 1280,
                channels -> new BatchNorm2dLayer(channels, 0.001, 0.01, true));
        var model = new VisionModel(net, transform);
        model.load(path);
        return model;
    }

    /**
     * EfficientNet-V2_M (larger) model.
     * @return the model.
     */
    public static VisionModel V2M() {
        return V2M("model/EfficientNet/efficientnet_v2_m.pt");
    }

    /**
     * EfficientNet-V2_M (larger) model.
     * @param path the pre-trained model file path.
     * @return the model.
     */
    public static VisionModel V2M(String path) {
        MBConvConfig[] config = {
                MBConvConfig.FusedMBConv(1, 3, 1, 24, 24, 3),
                MBConvConfig.FusedMBConv(4, 3, 2, 24, 48, 5),
                MBConvConfig.FusedMBConv(4, 3, 2, 48, 80, 5),
                MBConvConfig.MBConv(4, 3, 2, 80, 160, 7),
                MBConvConfig.MBConv(6, 3, 1, 160, 176, 14),
                MBConvConfig.MBConv(6, 3, 2, 176, 304, 18),
                MBConvConfig.MBConv(6, 3, 1, 304, 512, 5)
        };
        Transform transform = Transform.classification(480, 480);

        var net = new EfficientNet(config, 0.3, 0.2, 1000, 1280,
                channels -> new BatchNorm2dLayer(channels, 0.001, 0.01, true));
        var model = new VisionModel(net, transform);
        model.load(path);
        return model;
    }

    /**
     * EfficientNet-V2_L (largest) model.
     * @return the model.
     */
    public static VisionModel V2L() {
        return V2L("model/EfficientNet/efficientnet_v2_l.pt");
    }

    /**
     * EfficientNet-V2_L (largest) model.
     * @param path the pre-trained model file path.
     * @return the model.
     */
    public static VisionModel V2L(String path) {
        MBConvConfig[] config = {
                MBConvConfig.FusedMBConv(1, 3, 1, 32, 32, 4),
                MBConvConfig.FusedMBConv(4, 3, 2, 32, 64, 7),
                MBConvConfig.FusedMBConv(4, 3, 2, 64, 96, 7),
                MBConvConfig.MBConv(4, 3, 2, 96, 192, 10),
                MBConvConfig.MBConv(6, 3, 1, 192, 224, 19),
                MBConvConfig.MBConv(6, 3, 2, 224, 384, 25),
                MBConvConfig.MBConv(6, 3, 1, 384, 640, 7)
        };
        Transform transform = Transform.classification(480, 480,
                new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f}, Image.SCALE_SMOOTH);

        var net = new EfficientNet(config, 0.4, 0.2, 1000, 1280,
                channels -> new BatchNorm2dLayer(channels, 0.001, 0.01, true));
        var model = new VisionModel(net, transform);
        model.load(path);
        return model;
    }
}
