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
package smile.deep.layer;

import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.Module;
import smile.deep.tensor.Tensor;

/**
 * A convolutional layer.
 *
 * @author Haifeng Li
 */
public class Conv2dLayer implements Layer {
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
     * @param paddingMode "zeros", "reflect", "replicate" or "circular".
     */
    public Conv2dLayer(int in, int out, int kernel, int stride, int padding, int dilation, int groups, boolean bias, String paddingMode) {
        if (!(paddingMode.equals("zeros") || paddingMode.equals("reflect") || paddingMode.equals("replicate") || paddingMode.equals("circular"))) {
            throw new IllegalArgumentException("paddingMode has to be either 'zeros', 'reflect', 'replicate' or 'circular, but got " + paddingMode);
        }

        // kernel_size, stride, padding, dilation are ExpandingArray in C++,
        // which would "expand" to {x, y}. However, JavaCpp maps it to
        // LongPointer. So we have to manually expand it.
        LongPointer kernelPointer = new LongPointer(kernel, kernel);
        LongPointer stridePointer = new LongPointer(stride, stride);
        LongPointer paddingPointer = new LongPointer(padding, padding);
        LongPointer dilationPointer = new LongPointer(dilation, dilation);

        var options = new Conv2dOptions(in, out, kernelPointer);
        options.stride().put(stridePointer);
        options.padding().put(paddingPointer);
        options.dilation().put(dilationPointer);
        options.groups().put(groups);
        options.bias().put(bias);
        options.padding_mode().put(
                switch (paddingMode) {
                    case "reflect" -> new kReflect();
                    case "replicate" -> new kReplicate();
                    case "circular" -> new kCircular();
                    default -> new kZeros();
                }
        );
        module = new Conv2dImpl(options);

        kernelPointer.close();
        stridePointer.close();
        paddingPointer.close();
        dilationPointer.close();
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
     * @param paddingMode "zeros", "reflect", "replicate" or "circular".
     */
    public Conv2dLayer(int in, int out, int kernel, int stride, String padding, int dilation, int groups, boolean bias, String paddingMode) {
        if (!(padding.equals("valid") || padding.equals("same"))) {
            throw new IllegalArgumentException("padding has to be either 'valid' or 'same', but got " + padding);
        }

        if (!(paddingMode.equals("zeros") || paddingMode.equals("reflect") || paddingMode.equals("replicate") || paddingMode.equals("circular"))) {
            throw new IllegalArgumentException("paddingMode has to be either 'zeros', 'reflect', 'replicate' or 'circular, but got " + paddingMode);
        }

        // kernel_size is an ExpandingArray in C++, which would "expand" to {x, y}.
        // However, JavaCpp maps it to LongPointer. So we have to manually expand it.
        LongPointer kernelPointer = new LongPointer(kernel, kernel);
        LongPointer stridePointer = new LongPointer(stride, stride);
        LongPointer dilationPointer = new LongPointer(dilation, dilation);

        var options = new Conv2dOptions(in, out, kernelPointer);
        options.stride().put(stridePointer);
        options.padding().put(padding.equals("valid") ? new kValid() : new kSame());
        options.dilation().put(dilationPointer);
        options.groups().put(groups);
        options.bias().put(bias);
        options.padding_mode().put(
                switch (paddingMode) {
                    case "reflect" -> new kReflect();
                    case "replicate" -> new kReplicate();
                    case "circular" -> new kCircular();
                    default -> new kZeros();
                }
        );
        module = new Conv2dImpl(options);

        kernelPointer.close();
        stridePointer.close();
        dilationPointer.close();
    }

    @Override
    public Module asTorch() {
        return module;
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(module.forward(input.asTorch()));
    }
}
