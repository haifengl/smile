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
package smile.vision.layer;

import java.util.function.IntFunction;
import smile.deep.activation.ActivationFunction;
import smile.deep.activation.ReLU;
import smile.deep.layer.BatchNorm2dLayer;
import smile.deep.layer.Conv2dLayer;
import smile.deep.layer.Layer;
import smile.deep.layer.SequentialBlock;
import smile.deep.tensor.Tensor;

/**
 * Convolution2d-Normalization-Activation block.
 *
 * @author Haifeng Li
 */
public class Conv2dNormActivation extends SequentialBlock {
    private final Options options;
    private final Conv2dLayer conv;
    private final Layer norm;
    private final ActivationFunction activation;

    /**
     * Conv2dNormActivation configurations.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param kernel the window/kernel size.
     * @param stride controls the stride for the cross-correlation.
     * @param padding controls the amount of padding applied on both sides.
     * @param dilation controls the spacing between the kernel points.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param normLayer the functor to create the normalization layer.
     * @param activation the activation function.
     */
    public record Options(int in, int out, int kernel, int stride, int padding, int dilation, int groups,
                          IntFunction<Layer> normLayer, ActivationFunction activation) {

        /** Constructor. */
        public Options {
            if (padding < 0) {
                padding = (kernel - 1) / 2 * dilation;
            }
        }

        /**
         * Constructor.
         * @param in the number of input channels.
         * @param out the number of output channels/features.
         * @param kernel the window/kernel size.
         */
        public Options(int in, int out, int kernel) {
            this(in, out, kernel, BatchNorm2dLayer::new, new ReLU(true));
        }

        /**
         * Constructor.
         * @param in the number of input channels.
         * @param out the number of output channels/features.
         * @param kernel the window/kernel size.
         * @param normLayer the functor to create the normalization layer.
         * @param activation the activation function.
         */
        public Options(int in, int out, int kernel, IntFunction<Layer> normLayer, ActivationFunction activation) {
            this(in, out, kernel, 1, normLayer, activation);
        }

        /**
         * Constructor.
         * @param in the number of input channels.
         * @param out the number of output channels/features.
         * @param kernel the window/kernel size.
         * @param stride controls the stride for the cross-correlation.
         * @param normLayer the functor to create the normalization layer.
         * @param activation the activation function.
         */
        public Options(int in, int out, int kernel, int stride, IntFunction<Layer> normLayer, ActivationFunction activation) {
            this(in, out, kernel, stride, 1, normLayer, activation);
        }

        /**
         * Constructor.
         * @param in the number of input channels.
         * @param out the number of output channels/features.
         * @param kernel the window/kernel size.
         * @param stride controls the stride for the cross-correlation.
         * @param groups controls the connections between inputs and outputs.
         *              The in channels and out channels must both be divisible by groups.
         * @param normLayer the functor to create the normalization layer.
         * @param activation the activation function.
         */
        public Options(int in, int out, int kernel, int stride, int groups, IntFunction<Layer> normLayer, ActivationFunction activation) {
            this(in, out, kernel, stride, -1, 1, groups, normLayer, activation);
        }
    }

    /**
     * Constructor.
     * @param options the layer block configuration.
     */
    public Conv2dNormActivation(Options options) {
        super("Conv2dNormActivation");

        this.options = options;
        this.conv = new Conv2dLayer(options.in, options.out, options.kernel, options.stride, options.padding,
                options.dilation, options.groups, false, "zeros");
        this.norm = options.normLayer.apply(options.out);
        this.activation = options.activation;
        add(conv);
        add(norm);
        if (activation != null) {
            add(activation);
        }
    }

    @Override
    public String toString() {
        return String.format("Conv2dNormActivation(%s)", options.toString());
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor t1 = conv.forward(input);
        Tensor t2 = norm.forward(t1);
        t1.close();

        Tensor output = t2;
        if (activation != null) {
            output = activation.forward(t2);
            if (!activation.isInplace()) {
                t2.close();
            }
        }

        return output;
    }
}
