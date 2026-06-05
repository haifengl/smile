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

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * Group normalization. The input channels are separated into groups.
 * The mean and standard-deviation are calculated separately over each
 * group.
 *
 * @author Haifeng Li
 */
public class GroupNormLayer extends TypedLayer {
    /**
     * Constructor.
     * @param groups the number of groups to separate the channels into.
     *               The number of channels must be divisible by the number
     *               of groups.
     * @param channels the number of input channels in (N,C,H,W).
     */
    public GroupNormLayer(int groups, int channels) {
        this(groups, channels, 1E-05, true);
    }

    /**
     * Constructor.
     * @param groups the number of groups to separate the channels into.
     *               The number of channels must be divisible by the number
     *               of groups.
     * @param channels the number of input channels in (N,C,H,W).
     * @param eps a value added to the denominator for numerical stability.
     * @param affine when set to true, this layer has learnable affine parameters.
     */
    public GroupNormLayer(int groups, int channels, double eps, boolean affine) {
        super(create(groups, channels, eps, affine));
    }

    private static Handles create(int groups, int channels, double eps, boolean affine) {
        MemorySegment h = check(smile_groupnorm_create(groups, channels, eps, affine ? 1 : 0));
        MemorySegment m = check(smile_groupnorm_as_module(h));
        return new Handles(h, m, () -> {
            smile_module_free(m);
            smile_groupnorm_free(h);
        });
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(smile_groupnorm_forward(handle, input.handle()));
    }
}
