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
import org.bytedeco.pytorch.Module;
import org.bytedeco.pytorch.kSame;
import org.bytedeco.pytorch.kValid;
import smile.deep.tensor.Tensor;

/**
 * A convolutional layer.
 *
 * @author Haifeng Li
 */
public class Conv2dLayer implements Layer {
    /** The layer configuration. */
    private final Conv2dOptions options;
    /** Implementation. */
    private final Conv2dImpl module;

    /**
     * Constructor.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param kernel the window/kernel size.
     * @param stride controls the stride for the cross-correlation.
     * @param padding controls the amount of padding applied on both sides.
     * @param dilation controls the spacing between the kernel points.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     */
    public Conv2dLayer(int in, int out, int kernel, int stride, int padding, int dilation, int groups, boolean bias) {
        // kernel_size is an ExpandingArray in C++, which would "expand" to {x, y}.
        // However, JavaCpp maps it to LongPointer. So we have to manually expand it.
        LongPointer size = new LongPointer(kernel, kernel);
        options = new Conv2dOptions(in, out, size);
        options.stride().put(stride);
        options.padding().put(new LongPointer(padding, padding));
        options.dilation().put(dilation);
        options.groups().put(groups);
        options.bias().put(bias);
        module = new Conv2dImpl(options);
    }

    /**
     * Constructor.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param kernel the window/kernel size.
     * @param stride controls the stride for the cross-correlation.
     * @param padding "valid" or "same". With "valid" padding, there's no
     *               "made-up" padding inputs. It drops the right-most columns
     *               (or bottom-most rows). "same" tries to pad evenly left
     *               and right, but if the amount of columns to be added
     *               is odd, it will add the extra column to the right.
     *               If stride is 1, the layer's outputs will have the
     *               same spatial dimensions as its inputs.
     * @param dilation controls the spacing between the kernel points.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     */
    public Conv2dLayer(int in, int out, int kernel, int stride, String padding, int dilation, int groups, boolean bias) {
        if (!(padding.equals("valid") || padding.equals("same"))) {
            throw new IllegalArgumentException("padding has to be either 'valid' or 'same', but got " + padding);
        }

        // kernel_size is an ExpandingArray in C++, which would "expand" to {x, y}.
        // However, JavaCpp maps it to LongPointer. So we have to manually expand it.
        LongPointer size = new LongPointer(kernel, kernel);
        options = new Conv2dOptions(in, out, size);
        options.stride().put(stride);
        options.padding().put(padding.equals("valid") ? new kValid() : new kSame());
        options.dilation().put(dilation);
        options.groups().put(groups);
        options.bias().put(bias);
        module = new Conv2dImpl(options);
    }

    /**
     * Returns the convolutional layer configuration.
     * @return the convolutional layer configuration.
     */
    public Conv2dOptions options() {
        return options;
    }

    @Override
    public void register(String name, Module parent) {
        parent.register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor input) {
        return Tensor.of(module.forward(input.asTorch()));
    }
}
