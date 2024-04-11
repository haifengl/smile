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
import org.bytedeco.pytorch.BatchNorm2dImpl;
import org.bytedeco.pytorch.BatchNormOptions;
import org.bytedeco.pytorch.Module;
import smile.deep.tensor.Tensor;

/**
 * A normalization layer that re-centers and normalizes the output
 * of one layer before feeding it to another. Centering and scaling the
 * intermediate tensors has a number of beneficial effects, such as allowing
 * higher learning rates without exploding/vanishing gradients.
 *
 * @author Haifeng Li
 */
public class BatchNorm2dLayer implements Layer {
    /** The layer configuration. */
    BatchNormOptions options;
    /** Implementation. */
    BatchNorm2dImpl module;

    /**
     * Constructor.
     * @param in the number of input features.
     */
    public BatchNorm2dLayer(int in) {
        this(in, 1E-05, 0.1, true);
    }

    /**
     * Constructor.
     * @param in the number of input features.
     * @param eps a value added to the denominator for numerical stability.
     * @param momentum the value used for the running_mean and running_var
     *                computation. Can be set to 0.0 for cumulative moving average
     *                (i.e. simple average).
     * @param affine when set to true, this layer has learnable affine parameters.
     */
    public BatchNorm2dLayer(int in, double eps, double momentum, boolean affine) {
        this.options = new BatchNormOptions(new LongPointer(1).put(in));
        options.eps().put(eps);
        if (momentum > 0.0) options.momentum().put(momentum);
        options.affine().put(affine);
        this.module = new BatchNorm2dImpl(options);
    }

    /**
     * Returns the batch normalization layer configuration.
     * @return the batch normalization layer configuration.
     */
    public BatchNormOptions options() {
        return options;
    }

    @Override
    public void register(String name, Module parent) {
        this.module = parent.register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor input) {
        return Tensor.of(module.forward(input.asTorch()));
    }
}
