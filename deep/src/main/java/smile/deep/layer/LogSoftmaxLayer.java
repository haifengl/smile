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

import org.bytedeco.pytorch.LinearImpl;
import org.bytedeco.pytorch.global.torch;
import smile.deep.tensor.Tensor;

/**
 * A layer with log softmax activation function.
 *
 * @author Haifeng Li
 */
public class LogSoftmaxLayer implements Layer {
    /** The number of input features. */
    int in;
    /** The number of output features. */
    int out;
    /** Implementation. */
    LinearImpl module;

    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     */
    public LogSoftmaxLayer(int in, int out) {
        this.in = in;
        this.out = out;
        this.module = new LinearImpl(in, out);
    }

    @Override
    public void register(String name, Layer parent) {
        this.module = parent.asTorch().register_module(name, new LinearImpl(in, out));
    }

    @Override
    public Tensor forward(Tensor input) {
        org.bytedeco.pytorch.Tensor x = input.asTorch();
        x = torch.log_softmax(module.forward(x), 1);
        return Tensor.of(x);
    }

    @Override
    public LinearImpl asTorch() {
        return module;
    }
}
