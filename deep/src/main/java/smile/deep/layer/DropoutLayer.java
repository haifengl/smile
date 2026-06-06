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
 * A dropout layer that randomly zeroes some of the elements of
 * the input tensor with probability p during training. The zeroed
 * elements are chosen independently for each forward call and are
 * sampled from a Bernoulli distribution. Each channel will be zeroed
 * out independently on every forward call.
 * <p>
 * This has proven to be an effective technique for regularization
 * and preventing the co-adaptation of neurons as described in the
 * paper "Improving Neural Networks by Preventing Co-adaptation
 * of Feature Detectors".
 *
 * @author Haifeng Li
 */
public class DropoutLayer extends AbstractLayer {
    /**
     * Constructor.
     * @param p the dropout probability.
     */
    public DropoutLayer(double p) {
        this(p, false);
    }

    /**
     * Constructor.
     * @param p the dropout probability.
     * @param inplace true if the operation executes in-place.
     */
    public DropoutLayer(double p, boolean inplace) {
        super(create(p, inplace));
    }

    private static Handles create(double p, boolean inplace) {
        MemorySegment h = check(smile_dropout_create(p, inplace ? 1 : 0));
        MemorySegment m = check(smile_dropout_as_module(h));
        return new Handles(h, m, () -> {
            smile_module_free(m);
            smile_dropout_free(h);
        });
    }

    @Override
    public Tensor forward(Tensor input) {
        if (smile_dropout_is_training(handle) == 0) return input;
        return new Tensor(smile_dropout_forward(handle, input.handle()));
    }
}
