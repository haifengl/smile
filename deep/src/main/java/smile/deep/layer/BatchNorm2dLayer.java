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

import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Tensor;

import static smile.deep.tensor.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * A batch normalization layer that re-centers and normalizes the output
 * of one layer before feeding it to another. Centering and scaling the
 * intermediate tensors has a number of beneficial effects, such as allowing
 * higher learning rates without exploding/vanishing gradients.
 *
 * @author Haifeng Li
 */
public class BatchNorm2dLayer extends TypedLayer {
    /**
     * Constructor.
     * @param channels the number of input channels.
     */
    public BatchNorm2dLayer(int channels) {
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
    public BatchNorm2dLayer(int channels, double eps, double momentum, boolean affine) {
        super(create(channels, eps, momentum, affine));
        if (momentum < 0.0 || momentum > 1.0) {
            throw new IllegalArgumentException("momentum must be in [0, 1], but got " + momentum);
        }
    }

    private static Handles create(int channels, double eps, double momentum, boolean affine) {
        MemorySegment h = check(smile_batchnorm2d_create(channels, eps, momentum, affine ? 1 : 0));
        MemorySegment m = check(smile_batchnorm2d_as_module(h));
        return new Handles(h, m, () -> {
            smile_module_free(m);
            smile_batchnorm2d_free(h);
        });
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(smile_batchnorm2d_forward(handle, input.handle()));
    }
}
