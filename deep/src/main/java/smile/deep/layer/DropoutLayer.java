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

import org.bytedeco.pytorch.DropoutImpl;
import smile.deep.tensor.Tensor;

/**
 * A dropout layer that randomly zeroes some of the elements of
 * the input tensor with probability p during training. The zeroed
 * elements are chosen independently for each forward call and are
 * sampled from a Bernoulli distribution. Each channel will be zeroed
 * out independently on every forward call.
 *
 * This has proven to be an effective technique for regularization
 * and preventing the co-adaptation of neurons as described in the
 * paper "Improving Neural Networks by Preventing Co-adaptation
 * of Feature Detectors".
 *
 * @author Haifeng Li
 */
public class DropoutLayer implements Layer {
    /** The dropout probability. */
    double p;
    /** Implementation. */
    DropoutImpl module;

    /**
     * Constructor.
     * @param p the dropout probability.
     */
    public DropoutLayer(double p) {
        this.p = p;
        this.module = new DropoutImpl(p);
    }

    @Override
    public void register(String name, Layer parent) {
        this.module = parent.asTorch().register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor input) {
        return Tensor.of(module.forward(input.asTorch()));
    }

    @Override
    public DropoutImpl asTorch() {
        return module;
    }
}
