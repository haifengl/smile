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
package smile.deep.layer;

import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.pytorch.Conv2dImpl;
import org.bytedeco.pytorch.Conv2dOptions;
import smile.deep.tensor.Tensor;

/**
 * A convolutional layer.
 *
 * @author Haifeng Li
 */
public class Conv2dLayer implements Layer {
    /** The layer configuration. */
    Conv2dOptions options;
    /** Implementation. */
    Conv2dImpl module;

    /**
     * Constructor.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param size the window size.
     * @param stride controls the stride for the cross-correlation.
     * @param padding controls the amount of padding applied on both sides.
     * @param dilation controls the spacing between the kernel points.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     */
    public Conv2dLayer(int in, int out, int size, int stride, int padding, int dilation, int groups, boolean bias) {
        this.options = new Conv2dOptions(in, out, new LongPointer(1).put(size));
        options.stride().put(stride);
        options.padding().put(new LongPointer(1).put(padding));
        options.dilation().put(dilation);
        options.groups().put(groups);
        options.bias().put(bias);
        this.module = new Conv2dImpl(options);
    }

    /**
     * Returns the convolutional layer configuration.
     * @return the convolutional layer configuration.
     */
    public Conv2dOptions options() {
        return options;
    }

    @Override
    public void register(String name, LayerBlock block) {
        this.module = block.asTorch().register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor input) {
        return Tensor.of(module.forward(input.asTorch()));
    }
}
