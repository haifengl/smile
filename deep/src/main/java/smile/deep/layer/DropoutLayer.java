/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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

import org.bytedeco.pytorch.DropoutImpl;
import org.bytedeco.pytorch.DropoutOptions;
import org.bytedeco.pytorch.Module;
import smile.deep.tensor.Tensor;

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
public class DropoutLayer implements Layer {
    private final DropoutImpl module;

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
        DropoutOptions options = new DropoutOptions();
        options.p().put(p);
        options.inplace().put(inplace);
        this.module = new DropoutImpl(options);
    }

    @Override
    public Module asTorch() {
        return module;
    }

    @Override
    public Tensor forward(Tensor input) {
        if (!module.is_training()) return input;
        return new Tensor(module.forward(input.asTorch()));
    }
}
