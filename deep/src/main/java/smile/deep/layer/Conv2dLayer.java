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
import org.bytedeco.pytorch.global.torch;
import smile.deep.tensor.Tensor;

/**
 * A convolutional layer.
 *
 * @author Haifeng Li
 */
public class Conv2dLayer implements Layer {
    /** The layer configuration. */
    Conv2dOptions options;
    /** The max pooling kernel size. */
    int pool;
    /** Implementation. */
    Conv2dImpl module;

    /**
     * Constructor.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param size the window size.
     * @param stride controls the stride for the cross-correlation.
     * @param dilation controls the spacing between the kernel points.
     *                It is harder to describe, but this link has a nice
     *                visualization of what dilation does.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     * @param pool the max pooling kernel size. Sets it to zero to skip pooling.
     */
    public Conv2dLayer(int in, int out, int size, int stride, int dilation, int groups, boolean bias, int pool) {
        this.pool = pool;
        LongPointer p = new LongPointer(1).put(size);
        this.options = new Conv2dOptions(in, out, p);
        options.stride().put(stride);
        options.dilation().put(dilation);
        options.groups().put(groups);
        options.bias().put(bias);
        this.module = new Conv2dImpl(options);
    }

    @Override
    public void register(String name, Layer parent) {
        this.module = parent.asTorch().register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor input) {
        org.bytedeco.pytorch.Tensor x = input.asTorch();
        x = torch.relu(module.forward(x));
        if (pool > 0) {
            x = torch.max_pool2d(x, pool, pool);
        }
        return Tensor.of(x);
    }

    @Override
    public Conv2dImpl asTorch() {
        return module;
    }
}
