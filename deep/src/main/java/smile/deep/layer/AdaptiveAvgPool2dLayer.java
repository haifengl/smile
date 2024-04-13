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

import org.bytedeco.pytorch.AdaptiveAvgPool2dImpl;
import org.bytedeco.pytorch.LongOptional;
import org.bytedeco.pytorch.global.torch;
import org.bytedeco.pytorch.Module;
import smile.deep.tensor.Tensor;

/**
 * An adaptive average pooling that reduces a tensor by combining cells.
 * The output size should be specified and the stride and kernel-size are
 * automatically selected to adapt to the needs.
 *
 * @author Haifeng Li
 */
public class AdaptiveAvgPool2dLayer implements Layer {
    private final AdaptiveAvgPool2dImpl module;
    private final int height;
    private final int width;

    /**
     * Constructor.
     * @param size the output size.
     */
    public AdaptiveAvgPool2dLayer(int size) {
        this.height = size;
        this.width = size;
        this.module = new AdaptiveAvgPool2dImpl(new LongOptional(size));
    }

    /**
     * Constructor.
     * @param height the output height.
     * @param width the output width.
     */
    public AdaptiveAvgPool2dLayer(int height, int width) {
        this.height = height;
        this.width = width;
        // JavaCPP doesn't generate correct constructor.
        // https://github.com/bytedeco/javacpp-presets/issues/1492
        this.module = new AdaptiveAvgPool2dImpl(new LongOptional(height));
    }

    @Override
    public void register(String name, Module parent) {
        parent.register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor input) {
        //return Tensor.of(module.forward(input.asTorch()));
        // work around with functional API
        return Tensor.of(torch.adaptive_avg_pool2d(input.asTorch(), height, width));
    }
}
