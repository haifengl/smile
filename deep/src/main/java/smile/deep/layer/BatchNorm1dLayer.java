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

import org.bytedeco.pytorch.BatchNorm1dImpl;
import org.bytedeco.pytorch.BatchNormOptions;
import org.bytedeco.pytorch.Module;
import smile.deep.tensor.Tensor;

/**
 * A batch normalization layer that re-centers and normalizes the output
 * of one layer before feeding it to another. Centering and scaling the
 * intermediate tensors has a number of beneficial effects, such as allowing
 * higher learning rates without exploding/vanishing gradients.
 *
 * @author Haifeng Li
 */
public class BatchNorm1dLayer implements Layer {
    /** Implementation. */
    private final BatchNorm1dImpl module;

    /**
     * Constructor.
     * @param channels the number of input channels.
     */
    public BatchNorm1dLayer(int channels) {
        this(channels, 1E-05, 0.1, true);
    }

    /**
     * Constructor.
     * @param channels the number of input channels.
     * @param eps a value added to the denominator for numerical stability.
     * @param momentum the value used for the running_mean and running_var
     *                computation. Can be set to 0.0 for cumulative moving average
     *                (i.e. simple average).
     * @param affine when set to true, this layer has learnable affine parameters.
     */
    public BatchNorm1dLayer(int channels, double eps, double momentum, boolean affine) {
        var options = new BatchNormOptions(channels);
        options.eps().put(eps);
        if (momentum > 0.0) options.momentum().put(momentum);
        options.affine().put(affine);
        this.module = new BatchNorm1dImpl(options);
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
